package com.raxim.myscoutee.domain.platform.security.bootstrap;

public class OperatorBootstrapTokenInvalidException
        extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OperatorBootstrapTokenInvalidException() {
        super("Operator bootstrap token is invalid.");
    }
}
