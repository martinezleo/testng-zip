/**
 * 
 */
package com.thed.zbot.launcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import com.thed.launcher.DefaultZBotScriptLauncher;
import com.thed.model.TestcaseExecution;
import com.thed.service.soap.wsdl.TestResult;
import com.thed.util.ScriptUtil;

/**
 * @author zephyrDev
 * 
 */
public class TestNGZBotScriptLauncher extends DefaultZBotScriptLauncher {

	private static final String PASSED = "1";
	private static final String FAILED = "2";
	private Logger logger = Logger.getLogger(TestNGZBotScriptLauncher.class.getName());
	private Map<Class, TestcaseExecution> scheduleMap = new HashMap<Class, TestcaseExecution>();
	private Map<Long, TestResult> resultMap = new HashMap<Long, TestResult>();

	/**
	 * 
	 */
	public TestNGZBotScriptLauncher() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Loops thro' entire batch of testcases, collects unique test classes to run and add them to {@link #scheduleMap} ignoring duplicate classes.
	 * 
	 */
	@Override
	public void batchStart() {
		super.batchStart();
		TestResult tr = new TestResult();
		for (TestcaseExecution tcE : testcaseBatchExecution.getTestcaseExecutionList()) {
			Class classToRun = null;
			try {
				classToRun = Class.forName(tcE.getScriptPath());
			} catch (ClassNotFoundException e) {
				logger.log(Level.SEVERE, "Class not found " + e.getMessage());
			}
			if (classToRun == null) {
				logger.log(Level.SEVERE, "Skipping the run");
				continue;
			}
			if (!scheduleMap.containsKey(classToRun)) {
				logger.info("Adding script class " + tcE.getScriptPath());
				scheduleMap.put(classToRun, tcE);
				tr.setExecutionNotes("");
			} else {
				tr.setExecutionNotes("Script path is not unique, result wont be updated");
				tr.setStatus("10");
				logger.log(Level.SEVERE, " Script path is not unique, result wont be updated, testCase ID: "+ tcE.getTcId());
			}
			tr.setReleaseTestScheduleId(tcE.getId());
			resultMap.put(tcE.getId(), tr);
		}
		run();
	}

	/**
	 * Dynamically prepares entire suite of test classes that need to be run. 
	 * Adds a listener adapter for success and failure. This listener is called by testNG
	 * on completion of individual test. This adapter records the outcome to be sync'd later
	 * with Zephyr Server (at the end of test). 
	 */
	public void run() {
		try {
			Map<String, String> parameters = getTestParams();
			TestNG ng = new TestNG();
			logger.info("Setting Tests Finished");

			XmlSuite suite = new XmlSuite();
			suite.setName("Zephyr Suite");
			suite.setParameters(parameters);

			XmlTest test = new XmlTest(suite);
			test.setName("Zephyr Test");
			List<XmlClass> classes = new ArrayList<XmlClass>();
			for (Class cl : scheduleMap.keySet()) {
				classes.add(new XmlClass(cl.getName()));
			}
			test.setXmlClasses(classes);
			List<XmlTest> tests = new ArrayList<XmlTest>();
			tests.add(test);
			suite.setTests(tests);
			List<XmlSuite> suites = new ArrayList<XmlSuite>();
			suites.add(suite);
			ng.setXmlSuites(suites);
			// ng.setTestClasses(scheduleMap.keySet().toArray(new Class[0]));
			suite.setVerbose(9);

			ng.addListener(new TestListenerAdapter() {
				public void onTestSuccess(ITestResult tr) {
					logger.info("Test Passed " + tr.getMethod());
					TestcaseExecution tce = scheduleMap.get(tr.getTestClass().getRealClass());
					TestResult tRes = resultMap.get(tce.getId());
					if (!FAILED.equals(tRes.getStatus()))
						tRes.setStatus(PASSED);
					tRes.setExecutionNotes(tRes.getExecutionNotes() + "\n " + tr.getMethod() + "-Passed");
				}

				public void onTestFailure(ITestResult tr) {
					logger.info("Test Failed " + tr.getMethod());
					TestcaseExecution tce = scheduleMap.get(tr.getTestClass().getRealClass());
					TestResult tRes = resultMap.get(tce.getId());
					tRes.setStatus(FAILED);
					tRes.setExecutionNotes(tRes.getExecutionNotes() + "\n " + tr.getMethod() + "-Failed");
					// if(tr.getThrowable() != null){
					// tRes.setExecutionNotes(tRes.getExecutionNotes() +
					// tr.getThrowable().toString());
					// }
				}

				public void onFinish() {

				}
			});
			logger.info("Starting the run ");
			ng.run();

			logger.severe("All Tests Finished");
			for (Map.Entry<Class, TestcaseExecution> en : scheduleMap.entrySet()) {
				TestResult tRes = resultMap.get(en.getValue().getId());
				/*
				 * This call logs in and out with every invocation
				 * @TODO - make it efficient
				 */
				ScriptUtil.updateTestcaseExecutionResult(
						url,
						en.getValue(),
						Integer.parseInt(tRes.getStatus()),
						(new StringBuilder())
								.append(" Successfully executed on ")
								.append(agent.getAgentHostAndIp()).toString());
			}

			logger.info("All Done");
		} catch (Exception e) {
			logger.log(
					Level.SEVERE,
					(new StringBuilder())
							.append("exception in script execution. ")
							.append(e.getMessage()).toString());
			e.printStackTrace();
		}
	}

	/**
	 * These parameters could be hardcoded here or fetched from external
	 * file/env
	 * 
	 * @return parameter map
	 */
	private Map<String, String> getTestParams() {
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("selSvrAddr", "localhost");
		parameters.put("selPort", "4444");
		parameters.put("browser", "firefox");
		parameters.put("uiqUrl", "https://qa.mycompany.com:14900/");
		parameters.put("dbStr","jdbc:oracle:thin:@//qa.databases.oracle.mycompany.com:1521/qaDB");
		parameters.put("dbUname", "scott");
		parameters.put("dbPwd", "tiger");
		return parameters;
	}

	public void testcaseExecutionRun() {
		// No Default implementation needed
	}

}
