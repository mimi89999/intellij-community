package com.jetbrains.python;

import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

import java.util.Collection;

/**
 * @author yole
 */
public class PyFindUsagesTest extends PyLightFixtureTestCase {
  public void testInitUsages() {   // PY-292
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/InitUsages.py");
    assertEquals(1, usages.size());
  }

  public void testClassUsages() {   // PY-774
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/ClassUsages.py");
    assertEquals(1, usages.size());
  }

  public void testReassignedLocalUsages() { // PY-527
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/ReassignedLocalUsages.py");
    assertEquals(3, usages.size());
  }

  public void testImplicitlyResolvedUsages() {
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/ImplicitlyResolvedUsages.py");
    assertEquals(1, usages.size());
  }
}
