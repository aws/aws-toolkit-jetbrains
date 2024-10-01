using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.RdBackend.Common.Env;
using JetBrains.ReSharper.Psi.CSharp;
using JetBrains.Rider.Backend.Env;

namespace AWS.Psi
{
    [ZoneMarker]
    public class ZoneMarker : IRequire<ILanguageCSharpZone>, IRequire<IRiderPlatformZone>, IRequire<IReSharperHostCoreFeatureZone>
    {
    }
}
