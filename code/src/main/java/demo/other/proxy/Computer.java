package demo.other.proxy;

public class Computer implements ComputerIntf {
    @Override
    public int add(int i) {
//        System.out.println(this.getClass());
        return i + 1;
    }
}