using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SignLanguageInterpreter.API.Contexts;

namespace SignLanguageInterpreter.API.Controllers;

[ApiController]
[Authorize]
[Route("[controller]")]
public class UpdateController(AppDbContext dbContext) : ControllerBase
{
    private readonly AppDbContext _dbContext = dbContext;

    [HttpPost]
    public async Task<IActionResult> Check([FromBody] List<Entities.File> files)
    {
        var dict = files.ToDictionary(kvp => kvp.Name, kvp => kvp.Hash);
        var hashes = files.Select(f => f.Hash).ToHashSet();
        var matches = await _dbContext.Files.Where(f => hashes.Contains(f.Hash)).ToListAsync();
        matches = matches.Where(f => dict.ContainsKey(f.Name) && dict[f.Name] == f.Hash).ToList();

        var toBeDeleted = new HashSet<Entities.File>(files);
        toBeDeleted.ExceptWith(matches);

        hashes.ExceptWith(toBeDeleted.Select(f => f.Hash));

        var toBeAdded = await _dbContext.Files.Where(f => !hashes.Contains(f.Hash)).ToListAsync();

        return Ok(new { ToBeDeleted = toBeDeleted.Select(f => f.Name), ToBeAdded = toBeAdded.Select(f => f.Name) });
    }
}
