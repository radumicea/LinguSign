using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Identity.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore;
using SignLanguageInterpreter.API.Entities;

namespace SignLanguageInterpreter.API.Contexts;

public class AppDbContext(DbContextOptions<AppDbContext> options)
    : IdentityDbContext<User>(options)
{
    public DbSet<Entities.File> Files { get; set; } = null!;

     protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<User>().Ignore(x => x.AccessFailedCount);
        modelBuilder.Entity<User>().Ignore(x => x.Email);
        modelBuilder.Entity<User>().Ignore(x => x.EmailConfirmed);
        modelBuilder.Entity<User>().Ignore(x => x.LockoutEnd);
        modelBuilder.Entity<User>().Ignore(x => x.LockoutEnabled);
        modelBuilder.Entity<User>().Ignore(x => x.NormalizedEmail);
        modelBuilder.Entity<User>().Ignore(x => x.PhoneNumber);
        modelBuilder.Entity<User>().Ignore(x => x.PhoneNumberConfirmed);
        modelBuilder.Entity<User>().Ignore(x => x.TwoFactorEnabled);

        modelBuilder.Entity<User>().Property(u => u.TokensUsed).IsRequired().HasDefaultValue(0);

        modelBuilder.Ignore<IdentityUserLogin<string>>();

        modelBuilder.UseCollation("BINARY");
    }
}
