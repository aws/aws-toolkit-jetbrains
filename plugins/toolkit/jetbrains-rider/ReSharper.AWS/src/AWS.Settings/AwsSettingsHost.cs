using AWS.Daemon.Settings;
using AWS.Toolkit.Rider.Model;
using JetBrains.Annotations;
using JetBrains.Application.Parts;
using JetBrains.Application.Settings;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.DataContext;
using JetBrains.ReSharper.Daemon.Impl;
using JetBrains.ReSharper.Feature.Services.Protocol;

namespace AWS.Settings
{
    #if (PROFILE_2023_3 || PROFILE_2024_1 || PROFILE_2024_2)
    [SolutionComponent]
    #else
    [SolutionComponent(InstantiationEx.UnspecifiedDefault)]
    #endif
    public class AwsSettingsHost
    {
        public AwsSettingsHost(Lifetime lifetime, [NotNull] ISolution solution, [NotNull] ISettingsStore settingsStore)
        {
            var model = solution.GetProtocolSolution().GetAwsSettingModel();

            #if PROFILE_2026_1
            var dataContext = solution.ToDataContext();
            #else
            // 2026.2 made ToDataContext() an obsolete error in favour of ToDataContext2()
            var dataContext = solution.ToDataContext2();
            #endif
            var contextBoundSettingsStoreLive = settingsStore.BindToContextLive(lifetime, ContextRange.Smart(dataContext));

            model.ShowLambdaGutterMarks.Advise(lifetime, isEnabled =>
            {
                var entry = settingsStore.Schema.GetScalarEntry( (LambdaGutterMarkSettings s) => s.Enabled);
                contextBoundSettingsStoreLive.SetValue(entry, isEnabled, null);
                solution.GetComponent<DaemonImpl>().Invalidate();
            });
        }
    }
}
