using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.RdBackend.Common.Env;
using JetBrains.ReSharper.Psi.CSharp;
using JetBrains.Rider.Backend.Env;

namespace AWS.Psi
{
    [ZoneMarker]
    public class ZoneMarker : IRequire<ILanguageCSharpZone>, IRequire<IRiderPlatformZone>
        #if (PROFILE_2023_3 || PROFILE_2024_1 || PROFILE_2024_2)
        #else
        , IRequire<IReSharperHostCoreFeatureZone>
        #endif
    {
    }
}
