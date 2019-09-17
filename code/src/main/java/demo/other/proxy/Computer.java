package demo.other.proxy;

public class Computer implements ComputerIntf {
    @Override
    public int add(int i) {
        return i + 1;
    }
}