package socle.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StartBudgetTest : BasePlatformTestCase() {
    fun testFirstStartThenThreeRestartsInThreeMinutes() {
        val s = SocleProjectService(project).also { com.intellij.openapi.util.Disposer.register(testRootDisposable, it) }
        assertTrue(s.allowStart(0))
        assertTrue(s.allowStart(1_000)); assertTrue(s.allowStart(2_000)); assertTrue(s.allowStart(3_000))
        assertFalse(s.allowStart(4_000))
        assertTrue(s.allowStart(200_000))
    }
}
