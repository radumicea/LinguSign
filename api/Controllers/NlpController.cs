using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using Newtonsoft.Json;
using SignLanguageInterpreter.API.Entities;
using SignLanguageInterpreter.API.Helpers;
using SignLanguageInterpreter.API.Services;
using System.Text;

namespace SignLanguageInterpreter.API.Controllers;

[ApiController]
[Authorize]
[Route("[controller]")]
public class NlpController(ChatGptService service, IHttpClientFactory httpClientFactory, UserManager<User> userManager) : ControllerBase
{
    private const string SentenceEndpoint = "http://localhost:8000/sentence/";

    private readonly HttpClient _httpClient = httpClientFactory.CreateClient();

    [EnableRateLimiting("ChatGptRateLimitingPolicy")]
    [HttpPost("lexemes")]
    public async Task<IActionResult> BuildSentenceFromLexemes([FromBody] LexemesPayload lexemesPayload)
    {
        if (lexemesPayload.LexemesList.Count == 0 || lexemesPayload.LexemesList.Last().Count == 0 || lexemesPayload.LexemesList.Last().Any(l => l.Length == 0))
            return BadRequest("Can not be empty.");

        var userMessage = lexemesPayload.LexemesList.Select(JsonConvert.SerializeObject).ToList();

        var response = await service.DoRequestAsync(new ChatGptRequest
        {
            Model = GptModel.GPT_3_5_Turbo,
            SystemMessage = Constants.SystemMessageOneSentence,
            UserMessages = userMessage,
            AssistantMessages = lexemesPayload.SentenceList
        });

        if (response.StatusCode != 200)
            return StatusCode(response.StatusCode, response.Message);

        if (response.Message == "400 Bad Request")
            return BadRequest("Invalid input.");

        var user = await userManager.GetUserAsync(User);
        user!.TokensUsed += response.TokensUsed;
        await userManager.UpdateAsync(user);

        return Ok(response.Message);
    }

    [EnableRateLimiting("ChatGptRateLimitingPolicy")]
    [HttpPost("lexemesVariants")]
    public async Task<IActionResult> BuildSentenceVariantsFromLexemes([FromBody] LexemesVariantsPayload lexemesPayload)
    {
        if (lexemesPayload.LexemesList.Count == 0 || lexemesPayload.LexemesList.Last().Count == 0 || lexemesPayload.LexemesList.Last().Any(l => l.Length == 0))
            return BadRequest("Can not be empty.");

        if (lexemesPayload.NumVariants < 2 || lexemesPayload.NumVariants > 4)
            return BadRequest("Min 2, max 4 variants");

        var systemMessage = string.Format(Constants.SystemMessageManySentences, lexemesPayload.NumVariants);
        var userMessage = lexemesPayload.LexemesList.Select(JsonConvert.SerializeObject).ToList();
        var assistantMessages = lexemesPayload.SentenceVariantsList.Select(JsonConvert.SerializeObject).ToList();

        var response = await service.DoRequestAsync(new ChatGptRequest
        {
            Model = GptModel.GPT_3_5_Turbo,
            SystemMessage = systemMessage,
            UserMessages = userMessage,
            AssistantMessages = assistantMessages
        });

        if (response.StatusCode != 200)
            return StatusCode(response.StatusCode, response.Message);

        if (response.Message == "400 Bad Request")
            return BadRequest("Invalid input.");

        var user = await userManager.GetUserAsync(User);
        user!.TokensUsed += response.TokensUsed;
        await userManager.UpdateAsync(user);

        return Ok(response.Message);
    }

    [HttpPost("sentence")]
    public async Task<IActionResult> GetLexemesFromSentence([FromBody] SentencePayload sentencePayload)
    {
        var json = JsonConvert.SerializeObject(sentencePayload);
        var response = await _httpClient.PostAsync(SentenceEndpoint, new StringContent(json, Encoding.UTF8, "application/json"));
        response.EnsureSuccessStatusCode();
        return Ok(await response.Content.ReadAsStringAsync());
    }
}

public class LexemesPayload
{
    public List<List<string>> LexemesList { get; set; } = null!;
    public List<string> SentenceList { get; set; } = null!;
}

public class LexemesVariantsPayload
{
    public List<List<string>> LexemesList { get; set; } = null!;
    public List<List<string>> SentenceVariantsList { get; set; } = null!;
    public int NumVariants { get; set; }
}

public class SentencePayload
{
    [JsonProperty("sentence")]
    public string Sentence { get; set; } = null!;

    [JsonProperty("is_end")]
    public bool IsEnd { get; set; }
}
