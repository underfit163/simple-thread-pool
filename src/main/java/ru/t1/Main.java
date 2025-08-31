package ru.t1;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        SimpleThreadPool pool = new SimpleThreadPool(3);

        for (int i = 0; i < 10; i++) {
            final int id = i;
            pool.execute(() -> {
                System.out.println(Thread.currentThread().getName() + " start task " + id);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {}
                System.out.println(Thread.currentThread().getName() + " end task " + id);
            });
        }

        Thread.sleep(1000);
        pool.shutdown();
        pool.awaitTermination();

        System.out.println("All workers terminated. submitted=" + pool.getSubmittedCount() +
            ", completed=" + pool.getCompletedCount());
    }
}