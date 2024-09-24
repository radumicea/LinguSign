using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using SignLanguageInterpreter.API.AppSettings;
using SignLanguageInterpreter.API.DTOs;
using SignLanguageInterpreter.API.Entities;
using SignLanguageInterpreter.API.Services;
using System.Security.Claims;

namespace BVBInfo.API.Controllers.REST;

[Route("[controller]")]
[ApiController]
public sealed class AuthenticateController(
    UserManager<User> userManager,
    RoleManager<IdentityRole> roleManager,
    JwtService jwtService) : ControllerBase
{
    [HttpPost("Login")]
    public async Task<IActionResult> Login([FromBody] UserDto model)
    {
        var user = await userManager.FindByNameAsync(model.UserName);
        if (user is null)
            return NotFound();

        if (!await userManager.CheckPasswordAsync(user, model.Password))
            return Unauthorized();

        var now = DateTime.UtcNow;

        return Ok(new TokenDto
        {
            Token = await jwtService.GenerateAccessToken(user, now),
            RefreshToken = await jwtService.GenerateRefreshToken(user, now),
        });
    }

    [HttpPost("Register")]
    public async Task<IActionResult> Register([FromBody] UserDto model)
    {
        var userExists = await userManager.FindByNameAsync(model.UserName);
        if (userExists is not null)
            return StatusCode(StatusCodes.Status409Conflict);

        var user = new User
        {
            SecurityStamp = Guid.NewGuid().ToString(),
            UserName = model.UserName,
        };

        var result = await userManager.CreateAsync(user, model.Password);
        if (!result.Succeeded)
            return StatusCode(StatusCodes.Status500InternalServerError);

        if (!await roleManager.RoleExistsAsync(UserRoles.User))
            await roleManager.CreateAsync(new IdentityRole(UserRoles.User));

        await userManager.AddToRoleAsync(user, UserRoles.User);

        return Ok();
    }

    [HttpPost("RefreshToken")]
    public async Task<IActionResult> RefreshToken([FromBody] TokenDto model)
    {
        var token = model.Token;
        var refreshToken = model.RefreshToken;

        var principal = jwtService.GetPrincipalFromToken(token, false);
        if (principal is null)
            return Unauthorized();

        var userId = principal.Claims.Single(c => c.Type == ClaimTypes.NameIdentifier).Value;
        var user = await userManager.FindByIdAsync(userId);
        if (user is null || !jwtService.ValidateRefreshToken(user, refreshToken))
            return Unauthorized();

        var now = DateTime.UtcNow;

        return Ok(new TokenDto
        {
            Token = await jwtService.GenerateAccessToken(user, now),
            RefreshToken = await jwtService.GenerateRefreshToken(user, now)
        });
    }

    [Authorize]
    [HttpDelete("Logout")]
    public async Task<IActionResult> Logout()
    {
        var user = await userManager.GetUserAsync(User);
        if (user is null)
            return Ok();

        user.RefreshTokenJti = null;
        await userManager.UpdateAsync(user);

        return NoContent();
    }
}