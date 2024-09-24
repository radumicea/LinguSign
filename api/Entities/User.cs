using Microsoft.AspNetCore.Identity;

namespace SignLanguageInterpreter.API.Entities;

public class User : IdentityUser
{
    public string? RefreshTokenJti { get; set; }
    public int TokensUsed {  get; set; }
}
