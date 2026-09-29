package org.example;

public class ThreadDemo {

    static void main() throws InterruptedException {
        Thread t2 = Thread.ofVirtual().start(() -> System.out.println(Thread.currentThread().isVirtual()));
        Thread.sleep(1000);
        Thread t1 = Thread.ofPlatform().start(() -> System.out.println(Thread.currentThread().getName()));


    }
}

class MyThread extends Thread {
    public final String name;

    MyThread(String name) {
        super();
        this.name = name;
    }

    @Override
    public void run() {
        while (true) {
            System.out.println("Running thread " + name);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
