package ru.dexterity.compileta.api.domain;

public class CompilationInfo {

    private String className;
    private String testClassName;
    private String methodName;
    private String code;
    private String testCode;
    private String solutionCode;
    private String solutionMethodName;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getTestClassName() {
        return testClassName;
    }

    public void setTestClassName(String testClassName) {
        this.testClassName = testClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTestCode() {
        return testCode;
    }

    public void setTestCode(String testCode) {
        this.testCode = testCode;
    }

    public String getSolutionCode() {
        return solutionCode;
    }

    public void setSolutionCode(String solutionCode) {
        this.solutionCode = solutionCode;
    }

    public String getSolutionMethodName() {
        return solutionMethodName;
    }

    public void setSolutionMethodName(String solutionMethodName) {
        this.solutionMethodName = solutionMethodName;
    }
}
