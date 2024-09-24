using Azure.AI.OpenAI;

namespace SignLanguageInterpreter.API.Services;

public class ChatGptService
{
    private const int ExpectedTokensTolerance = 200;

    private readonly OpenAIClient _client;

    public ChatGptService(IConfiguration config)
    {
        var apiKey = config["ChatGptApiKey"]!;
        _client = new(apiKey);
    }

    public async Task<ChatGptResponse> DoRequestAsync(ChatGptRequest request)
    {
        if (request.UserMessages.Any(string.IsNullOrWhiteSpace) ||
            request.AssistantMessages.Any(string.IsNullOrWhiteSpace))
        {
            return new ChatGptResponse { Message = "Messages can not be empty.", StatusCode = 400 };
        }

        if (request.UserMessages.Count != request.AssistantMessages.Count + 1)
            return new ChatGptResponse { Message = "No user message.", StatusCode = 400 };

        var deploymentName = request.Model.GetDeploymentName();
        var contextSize = request.Model.GetContextSize();
        var encodings = Tiktoken.Encoding.ForModel(deploymentName);

        var expectedResponseNrTokens = encodings.CountTokens(request.UserMessages.Last());

        var totalExpectedNrTokens = encodings.CountTokens(request.SystemMessage ?? "");
        totalExpectedNrTokens += request.AssistantMessages.Sum(encodings.CountTokens);
        totalExpectedNrTokens += request.UserMessages.Sum(encodings.CountTokens);
        totalExpectedNrTokens += expectedResponseNrTokens;

        var userMessages = new LinkedList<string>(request.UserMessages);
        var assistantMessages = new LinkedList<string>(request.AssistantMessages);

        while (assistantMessages.Count > 0 && totalExpectedNrTokens > (contextSize - ExpectedTokensTolerance))
        {
            totalExpectedNrTokens -= encodings.CountTokens(userMessages.First!.Value);
            totalExpectedNrTokens -= encodings.CountTokens(assistantMessages.First!.Value);

            userMessages.RemoveFirst();
            assistantMessages.RemoveFirst();
        }

        if (totalExpectedNrTokens > (contextSize - ExpectedTokensTolerance))
            return new ChatGptResponse { Message = "Input larger than context size.", StatusCode = 400 };

        var options = new ChatCompletionsOptions
        {
            DeploymentName = deploymentName,
        };

        if (!string.IsNullOrWhiteSpace(request.SystemMessage))
        {
            options.Messages.Add(new ChatRequestSystemMessage(request.SystemMessage));
        }

        foreach (var (u, a) in userMessages.Zip(assistantMessages))
        {
            options.Messages.Add(new ChatRequestUserMessage(u));
            options.Messages.Add(new ChatRequestAssistantMessage(a));
        }

        options.Messages.Add(new ChatRequestUserMessage(request.UserMessages.Last()));

        var response = await _client.GetChatCompletionsAsync(options);
        var message = response.Value.Choices[0].Message.Content;

        return new ChatGptResponse
        {
            TokensUsed = totalExpectedNrTokens - expectedResponseNrTokens + encodings.CountTokens(message),
            StatusCode = response.GetRawResponse().Status,
            Message = message
        };
    }
}

public class ChatGptRequest
{
    public GptModel Model { get; set; } = GptModel.GPT_3_5_Turbo;
    public string? SystemMessage { get; set; }
    public IList<string> UserMessages { get; set; } = [];
    public IList<string> AssistantMessages { get; set; } = [];
}

public class ChatGptResponse
{
    public int TokensUsed { get; set; }
    public int StatusCode { get; set; }
    public string Message { get; set; } = null!;
}

public enum GptModel
{
    GPT_3_5_Turbo
}

public static class GptModelExtensions
{
    private static readonly IReadOnlyDictionary<GptModel, string> _names = new Dictionary<GptModel, string>
    {
        [GptModel.GPT_3_5_Turbo] = "gpt-3.5-turbo"
    };

    private static readonly IReadOnlyDictionary<GptModel, int> _contextSizes = new Dictionary<GptModel, int>
    {
        [GptModel.GPT_3_5_Turbo] = 4096
    };

    public static string GetDeploymentName(this GptModel model)
    {
        return _names[model];
    }

    public static int GetContextSize(this GptModel model)
    {
        return _contextSizes[model];
    }
}