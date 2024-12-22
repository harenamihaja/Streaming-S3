import server.CentralVideoServer;


public class Main {
    public static void main(String[] args) {
        System.out.println(CentralVideoServer.class.getDeclaredFields()[0].getType());
    }
}
