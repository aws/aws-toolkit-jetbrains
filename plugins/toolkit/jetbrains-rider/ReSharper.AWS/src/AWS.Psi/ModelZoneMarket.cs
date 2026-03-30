using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.Rider.Model;

// https://github.com/JetBrains/resharper-unity/blob/cfd107d555a0a13a4b37273340bf930621d0a3dd/resharper/resharper-unity/src/Unity.Rider/ModelZoneMarker.cs

// ReSharper disable once CheckNamespace
namespace AWS.Toolkit.Rider.Model
{
    // Zone marker for the generated models. Required to keep zone inspections happy because the generated code uses a
    // type from a namespace that requires IRiderModelZone. In practice, this doesn't cause issues
    // TODO: Look at moving the models to a better location or namespace?
    [ZoneMarker]
    public class ZoneMarker : IRequire<IRiderModelZone>
    {
    }
}
