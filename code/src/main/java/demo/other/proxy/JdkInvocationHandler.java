package demo.other.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class JdkInvocationHandler implements InvocationHandler {
    private ComputerIntf computerIntf;

    public JdkInvocationHandler(ComputerIntf computerIntf) {
        this.computerIntf = computerIntf;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return method.invoke(computerIntf, args);
    }
}