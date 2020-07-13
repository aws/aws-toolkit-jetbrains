using AWS.Daemon.RunMarkers;
using JetBrains.TextControl.DocumentMarkup;

#if !PROFILE_2020_2
[assembly:
    RegisterHighlighter(
        LambdaRunMarkerAttributeIds.LAMBDA_RUN_METHOD_MARKER_ID,
        GutterMarkType = typeof(LambdaMethodRunMarkerGutterMark),
        EffectType = EffectType.GUTTER_MARK,
        Layer = HighlighterLayer.SYNTAX + 1
    )
]
#endif

namespace AWS.Daemon.RunMarkers
{
#if PROFILE_2020_2
    [RegisterHighlighter(
        LambdaRunMarkerAttributeIds.LAMBDA_RUN_METHOD_MARKER_ID,
        GutterMarkType = typeof(LambdaMethodRunMarkerGutterMark),
        EffectType = EffectType.GUTTER_MARK,
        Layer = HighlighterLayer.SYNTAX + 1
    )]
#endif
    public static class LambdaRunMarkerAttributeIds
    {
        public const string LAMBDA_RUN_METHOD_MARKER_ID = "AWS Lambda Run Method Gutter Mark";
    }
}
