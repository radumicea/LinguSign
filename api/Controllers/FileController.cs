using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SignLanguageInterpreter.API.AppSettings;
using SignLanguageInterpreter.API.Contexts;
using SignLanguageInterpreter.API.Helpers;
using System.Runtime.InteropServices;
using System.Security.Cryptography;

namespace SignLanguageInterpreter.API.Controllers;

[ApiController]
[Authorize]
[Route("[controller]")]
public class FileController(AppDbContext dbContext) : ControllerBase
{
    private const string FileContentType = "application/octet-stream";

    private static readonly string _filesPath;

    private readonly AppDbContext _dbContext = dbContext;

    static FileController()
    {
        string srv;
        if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            var root = Path.GetPathRoot(Environment.GetFolderPath(Environment.SpecialFolder.System))!;
            srv = Path.Combine(root, "srv");
        }
        else
        {
            srv = "/srv";
        }

        Directory.CreateDirectory(srv);

        _filesPath = Path.Combine(srv, "SignLanguageInterpreter.API");
        Directory.CreateDirectory(_filesPath);
    }

    [HttpGet]
    public async Task<IActionResult> Get()
    {
        return Ok((await _dbContext.Files.Select(f => f.Name).ToListAsync()).NaturalOrderBy(x => x));
    }

    [AllowAnonymous]
    [HttpGet("{directory}/{fileName}")]
    public IActionResult Get([FromRoute] string directory, [FromRoute] string fileName)
    {
        directory = directory.ToLowerInvariant();
        fileName = fileName.ToLowerInvariant();

        var fullPath = Path.Combine(_filesPath, directory, fileName);
        if (!System.IO.File.Exists(fullPath))
            return NotFound();

        var fs = new FileStream(fullPath, FileMode.Open, FileAccess.Read, FileShare.Read, 4096, true);

        return File(fs, FileContentType, fileName);
    }

    [Authorize(Roles = UserRoles.Admin)]
    [HttpPost("{directory}")]
    public async Task<IActionResult> Post([FromRoute] string directory, IFormFile file)
    {
        directory = directory.ToLowerInvariant();
        Directory.CreateDirectory(Path.Combine(_filesPath, directory));

        var fileName = file.FileName.ToLowerInvariant();

        var path = Path.Combine(directory, fileName);

        await using var ms = new MemoryStream();
        await file.CopyToAsync(ms);

        ms.Position = 0;
        using var md5 = MD5.Create();
        var hash = Convert.ToBase64String(await md5.ComputeHashAsync(ms));

        var fullPath = Path.Combine(_filesPath, path);

        ms.Position = 0;
        await using var fs = new FileStream(fullPath, FileMode.Create, FileAccess.Write, FileShare.None, 4096, true);
        await ms.CopyToAsync(fs);

        var dbName = directory + "/" + fileName;

        var entity = await _dbContext.Files.SingleOrDefaultAsync(f => f.Name == dbName);
        if (entity is not null)
            entity.Hash = hash;
        else
            await _dbContext.AddAsync(new Entities.File
            {
                Name = dbName,
                Hash = hash
            });

        await _dbContext.SaveChangesAsync();

        return Created();
    }

    [Authorize(Roles = UserRoles.Admin)]
    [HttpDelete("{directory}/{fileName}")]
    public async Task<IActionResult> Delete([FromRoute] string directory, [FromRoute] string fileName)
    {
        directory = directory.ToLowerInvariant();
        fileName = fileName.ToLowerInvariant();

        var path = Path.Combine(directory, fileName);

        var fullPath = Path.Combine(_filesPath, path);
        if (!System.IO.File.Exists(fullPath))
            return NoContent();

        System.IO.File.Delete(fullPath);

        var dbName = directory + "/" + fileName;

        var entity = await _dbContext.Files.SingleOrDefaultAsync(f => f.Name == dbName);
        if (entity is not null)
        {
            _dbContext.Files.Remove(entity);
            await _dbContext.SaveChangesAsync();
        }

        return NoContent();
    }
}
