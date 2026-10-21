using Xunit;

namespace BpmnToCode.Runtime.Tests;

public class ConsumerTest
{
    [Fact]
    public void ConsumerCodeNavigatesTwoGeneratedApisOfOneRun()
    {
        // when
        var exception = Record.Exception(MiraVelo.Process.Consumer.Consumer.Navigate);

        // then
        Assert.Null(exception);
    }
}
