using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using SignLanguageInterpreter.API.AppSettings;
using SignLanguageInterpreter.API.Contexts;
using SignLanguageInterpreter.API.Entities;
using SignLanguageInterpreter.API.Services;
using System.Text.Json.Serialization;
using System.Threading.RateLimiting;

const string myPolicy = "MyPolicy";

var builder = WebApplication.CreateBuilder(args);

builder.WebHost.ConfigureKestrel(serverOptions =>
{
    serverOptions.Limits.MaxRequestBodySize = 128 * 1024 * 1024;
});

builder.Services.AddCors(options =>
{
    options.AddPolicy(myPolicy, policy =>
    {
        policy.AllowAnyOrigin()
            .AllowAnyHeader()
            .AllowAnyMethod();
    });
});

builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.AddPolicy(policyName: "ChatGptRateLimitingPolicy", partitioner: httpContext =>
    {
        var accessToken = httpContext.Request.Headers.Authorization.ToString().Replace("Bearer ", "");

        return RateLimitPartition.GetTokenBucketLimiter(accessToken, _ =>
            new TokenBucketRateLimiterOptions
            {
                TokenLimit = 1,
                QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
                QueueLimit = 1,
                ReplenishmentPeriod = TimeSpan.FromSeconds(1),
                TokensPerPeriod = 1,
                AutoReplenishment = true
            });
    });
});

builder.Services.AddControllers().AddJsonOptions(options => options.JsonSerializerOptions.ReferenceHandler = ReferenceHandler.IgnoreCycles);

builder.Services.AddDbContext<AppDbContext>(options =>
{
    var connectionString = builder.Configuration.GetConnectionString("Database");
    options.UseSqlite(connectionString);
});

builder.Services.AddScoped<JwtService>();
builder.Services.AddSingleton<ChatGptService>();

builder.Services.AddHttpClient();

// For Identity
builder.Services.AddIdentity<User, IdentityRole>(opt =>
{
    opt.Password.RequireUppercase = false;
})
    .AddEntityFrameworkStores<AppDbContext>()
    .AddDefaultTokenProviders();

// Adding Authentication
builder.Services.AddAuthentication(static options =>
{
    options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
    options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
    options.DefaultScheme = JwtBearerDefaults.AuthenticationScheme;
})
    // Adding Jwt Bearer
    .AddJwtBearer(options =>
    {
        options.SaveToken = true;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ClockSkew = TimeSpan.Zero,

            ValidAudience = builder.Configuration["JWT:ValidAudience"],
            ValidIssuer = builder.Configuration["JWT:ValidIssuer"],
            IssuerSigningKey = new SymmetricSecurityKey(Convert.FromHexString(builder.Configuration["JWT:Secret"]!))
        };
    });

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.AddSecurityDefinition("JWT",
        new OpenApiSecurityScheme
        {
            Description = "JWT Authorization header using the Bearer scheme.",
            Name = "Authorization",
            In = ParameterLocation.Header,
            Type = SecuritySchemeType.Http,
            Scheme = "bearer"
        });

    c.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference
                {
                    Type = ReferenceType.SecurityScheme,
                    Id = "JWT"
                }
            },
            new List<string>()
        }
    });
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

using (var scope = app.Services.CreateScope())
{
    var services = scope.ServiceProvider;
    var context = services.GetRequiredService<AppDbContext>();
    var userManager = services.GetRequiredService<UserManager<User>>();
    var roleManager = services.GetRequiredService<RoleManager<IdentityRole>>();

    if (await context.Database.EnsureCreatedAsync())
    {
        var admins = builder.Configuration.GetSection("Admins").GetChildren();

        foreach (var admin in admins)
        {
            User user = new()
            {
                SecurityStamp = Guid.NewGuid().ToString(),
                UserName = (string)admin.GetValue(typeof(string), "UserName")!,
            };

            await userManager.CreateAsync(user, (string)admin.GetValue(typeof(string), "Password")!);
            await roleManager.CreateAsync(new IdentityRole(UserRoles.Admin));
            await userManager.AddToRoleAsync(user, UserRoles.Admin);
            await context.SaveChangesAsync();
        }
    }
}

app.UseHttpsRedirection();

app.UseCors(myPolicy);

app.UseAuthentication();
app.UseAuthorization();
app.UseRateLimiter();

app.MapControllers();

app.Run();
