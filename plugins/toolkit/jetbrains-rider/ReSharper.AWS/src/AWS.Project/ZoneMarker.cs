using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ReSharper.Psi.CSharp;
using JetBrains.Rider.Model;

namespace AWS.Project
{
    [ZoneMarker]
    public class ZoneMarker : IRequire<ILanguageCSharpZone>, IRequire<IRiderModelZone>
    {
    }
}
