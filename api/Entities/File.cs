using Microsoft.EntityFrameworkCore;
using System.ComponentModel.DataAnnotations;

namespace SignLanguageInterpreter.API.Entities;

[Index(nameof(Hash))]
public class File
{
    [Key]
    public string Name { get; set; } = null!;
    public string Hash { get; set; } = null!;

    public override int GetHashCode()
    {
        return HashCode.Combine(Name, Hash);
    }

    public override bool Equals(object? obj)
    {
        return obj is File f && f.Name == Name && f.Hash == Hash;
    }
}
