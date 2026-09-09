package com.hybrid.framework.listeners;

import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.healing.HealingEventStore;
import com.hybrid.framework.healing.HealingUiDriver;
import com.hybrid.framework.reporting.AllureSupport;
import com.hybrid.framework.reporting.ExecutionResultStore;
import com.hybrid.framework.reporting.PdfReportGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IExecutionListener;

import java.nio.file.Path;

public final class ReportLifecycleListener implements IExecutionListener {
    private static final Logger LOGGER = LogManager.getLogger(ReportLifecycleListener.class);

    @Override
    public void onExecutionStart() {
        ExecutionResultStore.clear();
        HealingEventStore.clear();
        HealingUiDriver.clearCache();
        AllureSupport.writeEnvironmentProperties();
        LOGGER.info("EXECUTION_START engine={} browser={} dataSource={}",
                FrameworkConfig.engine(), FrameworkConfig.browser(), FrameworkConfig.dataSource());
    }

    @Override
    public void onExecutionFinish() {
        if (FrameworkConfig.pdfReportEnabled()) {
            Path path = PdfReportGenerator.generate(ExecutionResultStore.snapshot(), HealingEventStore.snapshot());
            LOGGER.info("PDF_REPORT_GENERATED path={}", path.toAbsolutePath());
        }
        LOGGER.info("EXECUTION_FINISH");
    }
}
