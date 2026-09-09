package com.hybrid.framework.listeners;

import com.hybrid.framework.config.FrameworkConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IAlterSuiteListener;
import org.testng.xml.XmlSuite;

import java.util.List;

public final class SuiteConfigurationListener implements IAlterSuiteListener {
    private static final Logger LOGGER = LogManager.getLogger(SuiteConfigurationListener.class);

    @Override
    public void alter(List<XmlSuite> suites) {
        for (XmlSuite suite : suites) {
            suite.setParallel(resolve(FrameworkConfig.parallelMode()));
            suite.setThreadCount(FrameworkConfig.threadCount());
            suite.setDataProviderThreadCount(FrameworkConfig.dataProviderThreadCount());
            String source = FrameworkConfig.dataSource();
            if (!"both".equals(source)) {
                suite.getTests().removeIf(test ->
                        ("feature".equals(source) && !test.getName().toLowerCase().contains("feature"))
                                || ("excel".equals(source) && !test.getName().toLowerCase().contains("excel")));
            }
            LOGGER.info("SUITE_CONFIGURATION suite={} dataSource={} parallel={} threads={} dpThreads={}",
                    suite.getName(), source, suite.getParallel(), suite.getThreadCount(), suite.getDataProviderThreadCount());
        }
    }

    private XmlSuite.ParallelMode resolve(String value) {
        return switch (value) {
            case "none" -> XmlSuite.ParallelMode.NONE;
            case "methods" -> XmlSuite.ParallelMode.METHODS;
            case "classes" -> XmlSuite.ParallelMode.CLASSES;
            case "tests" -> XmlSuite.ParallelMode.TESTS;
            case "instances" -> XmlSuite.ParallelMode.INSTANCES;
            default -> throw new IllegalArgumentException("Unsupported parallel mode " + value);
        };
    }
}
