using AWS.Toolkit.Rider.Model;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Protocol;

namespace AWS.Daemon.Lambda
{
    #if (PROFILE_2023_3 || PROFILE_2024_1 || PROFILE_2024_2)
    [SolutionComponent]
    #else
    [SolutionComponent(InstantiationEx.UnspecifiedDefault)]
    #endif
    public class LambdaDaemonHost
    {
        private readonly LambdaDaemonModel _model;

        public LambdaDaemonHost(ISolution solution)
        {
            _model = solution.GetProtocolSolution().GetLambdaDaemonModel();
        }

        public void RunLambda(string methodName, string handler)
        {
            _model.RunLambda(new LambdaRequest(methodName, handler));
        }

        public void DebugLambda(string methodName, string handler)
        {
            _model.DebugLambda(new LambdaRequest(methodName, handler));
        }

        public void CreateNewLambda(string methodName, string handler)
        {
            _model.CreateNewLambda(new LambdaRequest(methodName, handler));
        }
    }
}
