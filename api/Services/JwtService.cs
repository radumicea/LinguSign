using Microsoft.AspNetCore.Identity;
using Microsoft.IdentityModel.Tokens;
using SignLanguageInterpreter.API.Entities;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;

namespace SignLanguageInterpreter.API.Services;

public class JwtService(IConfiguration configuration, UserManager<User> userManager)
{
    public async Task<string> GenerateAccessToken(User user, DateTime now)
    {
        var roles = await userManager.GetRolesAsync(user);

        var claims = new List<Claim>
        {
            new(ClaimTypes.Name, user.UserName!),
            new(ClaimTypes.NameIdentifier, user.Id),
        };
        claims.AddRange(roles.Select(r => new Claim(ClaimTypes.Role, r)));

        var tokenValidityInMinutes = double.Parse(configuration["JWT:TokenValidityInMinutes"]!);

        return GenerateToken(claims, now.AddMinutes(tokenValidityInMinutes));
    }

    public async Task<string> GenerateRefreshToken(User user, DateTime now)
    {
        var jti = Guid.NewGuid().ToString();

        user.RefreshTokenJti = jti;
        await userManager.UpdateAsync(user);

        var claims = new List<Claim>
        {
            new(JwtRegisteredClaimNames.Jti, jti),
        };

        var refreshTokenValidityInDays = double.Parse(configuration["JWT:RefreshTokenValidityInDays"]!);

        return GenerateToken(claims, now.AddDays(refreshTokenValidityInDays));
    }

    public ClaimsPrincipal? GetPrincipalFromToken(string token, bool validateLifetime)
    {
        try
        {
            var tokenValidationParameters = new TokenValidationParameters
            {
                ValidateIssuer = true,
                ValidateAudience = true,
                ValidateLifetime = validateLifetime,
                ValidateIssuerSigningKey = true,
                ClockSkew = TimeSpan.Zero,
                
                ValidAudience = configuration["JWT:ValidAudience"],
                ValidIssuer = configuration["JWT:ValidIssuer"],
                IssuerSigningKey = new SymmetricSecurityKey(Convert.FromHexString(configuration["JWT:Secret"]!))
            };

            var tokenHandler = new JwtSecurityTokenHandler();
            var principal =
                tokenHandler.ValidateToken(token, tokenValidationParameters, out var securityToken);

            if (securityToken is not JwtSecurityToken jwtSecurityToken ||
                !jwtSecurityToken.Header.Alg.Equals(SecurityAlgorithms.HmacSha256,
                    StringComparison.InvariantCultureIgnoreCase))
                return null;

            return principal;
        }
        catch
        {
            return null;
        }
    }

    public bool ValidateRefreshToken(User user, string refreshToken)
    {
        var principal = GetPrincipalFromToken(refreshToken, true);

        if (principal is null || user.RefreshTokenJti != principal.Claims.Single(c => c.Type == JwtRegisteredClaimNames.Jti).Value)
            return false;

        return true;
    }

    private string GenerateToken(IList<Claim> claims, DateTime exp)
    {
        var authSigningKey = new SymmetricSecurityKey(Convert.FromHexString(configuration["JWT:Secret"]!));

        var token = new JwtSecurityToken(
            issuer: configuration["JWT:ValidIssuer"],
            audience: configuration["JWT:ValidAudience"],
            expires: exp,
            claims: claims,
            signingCredentials: new SigningCredentials(authSigningKey, SecurityAlgorithms.HmacSha256)
        );

        return new JwtSecurityTokenHandler().WriteToken(token);
    }
}
