package com.reliaquest.api.exception;

public class EmployeeNotFoundException extends RuntimeException {
    private final String employeeId;

    public EmployeeNotFoundException(String employeeId) {
        super("Employee not found with ID: " + employeeId);
        this.employeeId = "employeeId";
    }

    public String getEmployeeId() {
        return employeeId;
    }
}
