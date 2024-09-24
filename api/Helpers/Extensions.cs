using NaturalSort.Extension;

namespace SignLanguageInterpreter.API.Helpers;

public static class Extensions
{
    public static IOrderedEnumerable<T> NaturalOrderBy<T>(this IEnumerable<T> source, Func<T, string> keySelector)
    {
        return source.OrderBy(keySelector, StringComparison.CurrentCultureIgnoreCase.WithNaturalSort());
    }
}
