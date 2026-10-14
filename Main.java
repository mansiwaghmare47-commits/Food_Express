import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Online Food Ordering & Delivery System (Console)
 * Concepts: OOP, Collections, Multithreading, Exception Handling
 */
public class Main {

    private static int readInt(Scanner sc, String prompt) {
        System.out.print(prompt);
        try {
            return Integer.parseInt(sc.nextLine().trim());
        } catch (NumberFormatException e) {          // Exception handling
            System.out.println("  ! Please enter a valid number.");
            return -1;
        }
    }

    public static void main(String[] args) {
        FoodService foodService = new FoodService();
        DeliveryService delivery = new DeliveryService();
        Map<MenuItem, Integer> cart = new LinkedHashMap<>();
        delivery.start();                              // starts 3 delivery threads

        try (Scanner sc = new Scanner(System.in)) {    // try-with-resources
            System.out.println("====== WELCOME TO JAVA FOOD EXPRESS ======");
            System.out.print("Enter your name: ");
            String name = sc.nextLine().trim();
            if (name.isEmpty()) name = "Guest";

            boolean exit = false;
            while (!exit) {
                System.out.println("\n1. View Menu");
                System.out.println("2. Add Item to Cart");
                System.out.println("3. View Cart");
                System.out.println("4. Place Order");
                System.out.println("5. Track Orders");
                System.out.println("6. Exit");
                int choice = readInt(sc, "Choose: ");

                try {
                    switch (choice) {
                        case 1:
                            foodService.showMenu();
                            break;
                        case 2:
                            int id = readInt(sc, "Item ID: ");
                            int qty = readInt(sc, "Quantity: ");
                            foodService.addToCart(cart, id, qty);
                            System.out.println("  Added to cart.");
                            break;
                        case 3:
                            if (cart.isEmpty()) System.out.println("  Cart is empty.");
                            else cart.forEach((item, q) ->
                                    System.out.printf("  %-22s x%d%n", item.getName(), q));
                            break;
                        case 4:
                            Order order = foodService.createOrder(name, cart);
                            order.printBill();
                            delivery.submitOrder(order);   // handed to delivery threads
                            cart.clear();
                            System.out.println("Order placed! Delivery agents are on it.");
                            break;
                        case 5:
                            foodService.showOrders();
                            break;
                        case 6:
                            exit = true;
                            break;
                        default:
                            if (choice != -1) System.out.println("  ! Invalid choice (1-6).");
                    }
                } catch (ItemNotFoundException | InvalidQuantityException | EmptyCartException e) {
                    System.out.println("  ! " + e.getMessage());   // multi-catch
                } catch (Exception e) {
                    System.out.println("  ! Unexpected error: " + e);
                }
            }
        } finally {                                    // always runs
            if (delivery.getPendingCount() > 0 || foodService.getTotalOrders() > delivery.getDeliveredCount()) {
                System.out.println("\nWaiting for pending deliveries to finish...");
            }
            delivery.stop();
            System.out.println("\n=========== SUMMARY ===========");
            System.out.println("Total orders     : " + foodService.getTotalOrders());
            System.out.println("Delivered        : " + delivery.getDeliveredCount());
            System.out.println("Unique customers : " + foodService.getUniqueCustomerCount());
            System.out.println("Thank you! Goodbye.");
        }
    }
}

/**
 * OOP: Abstraction + Encapsulation.
 * Abstract parent class for every food item on the menu.
 */
abstract class MenuItem {
    private final int id;
    private final String name;
    private final double price;

    public MenuItem(int id, String name, double price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }

    // Each child class must define its own category (Polymorphism)
    public abstract String getCategory();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MenuItem)) return false;
        return id == ((MenuItem) o).id;
    }

    @Override
    public int hashCode() { return Integer.hashCode(id); }

    @Override
    public String toString() {
        return String.format("%-4d %-22s %-10s Rs.%.2f", id, name, getCategory(), price);
    }
}

/** OOP: Inheritance */
class VegItem extends MenuItem {
    public VegItem(int id, String name, double price) { super(id, name, price); }
    @Override public String getCategory() { return "Veg"; }
}

/** OOP: Inheritance */
class NonVegItem extends MenuItem {
    public NonVegItem(int id, String name, double price) { super(id, name, price); }
    @Override public String getCategory() { return "Non-Veg"; }
}

/** OOP: Inheritance */
class BeverageItem extends MenuItem {
    public BeverageItem(int id, String name, double price) { super(id, name, price); }
    @Override public String getCategory() { return "Beverage"; }
}

/** OOP: Interface */
interface Discountable {
    double applyDiscount(double amount);
}

enum OrderStatus {
    PLACED, PREPARING, OUT_FOR_DELIVERY, DELIVERED
}

/** Exception Handling: custom checked exception */
class ItemNotFoundException extends Exception {
    public ItemNotFoundException(int id) {
        super("No menu item found with ID " + id);
    }
}

/** Exception Handling: custom checked exception */
class InvalidQuantityException extends Exception {
    public InvalidQuantityException(int qty) {
        super("Invalid quantity: " + qty + " (allowed: 1 to 20)");
    }
}

/** Exception Handling: custom checked exception */
class EmptyCartException extends Exception {
    public EmptyCartException() {
        super("Your cart is empty. Add items before placing an order.");
    }
}


/**
 * Collections: LinkedHashMap keeps items in the order they were added.
 * OOP: implements the Discountable interface.
 * Multithreading: volatile status + AtomicInteger id generator (thread-safe).
 */
class Order implements Discountable {
    private static final AtomicInteger counter = new AtomicInteger(1000);

    private final int orderId;
    private final String customerName;
    private final Map<MenuItem, Integer> items;
    private volatile OrderStatus status = OrderStatus.PLACED;

    public Order(String customerName, Map<MenuItem, Integer> cart) {
        this.orderId = counter.incrementAndGet();
        this.customerName = customerName;
        this.items = new LinkedHashMap<>(cart);
    }

    public double getSubtotal() {
        double total = 0;
        for (Map.Entry<MenuItem, Integer> e : items.entrySet()) {
            total += e.getKey().getPrice() * e.getValue();
        }
        return total;
    }

    // 10% discount on orders above Rs.500
    @Override
    public double applyDiscount(double amount) {
        return amount > 500 ? amount * 0.10 : 0;
    }

    public double getFinalAmount() {
        double sub = getSubtotal();
        return sub - applyDiscount(sub);
    }

    public int getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public void printBill() {
        System.out.println("\n------------- BILL (Order #" + orderId + ") -------------");
        System.out.println("Customer: " + customerName);
        for (Map.Entry<MenuItem, Integer> e : items.entrySet()) {
            System.out.printf("  %-22s x%-2d  Rs.%.2f%n",
                    e.getKey().getName(), e.getValue(), e.getKey().getPrice() * e.getValue());
        }
        double sub = getSubtotal();
        System.out.printf("  Subtotal : Rs.%.2f%n", sub);
        System.out.printf("  Discount : Rs.%.2f%n", applyDiscount(sub));
        System.out.printf("  TOTAL    : Rs.%.2f%n", getFinalAmount());
        System.out.println("-----------------------------------------------");
    }

    @Override
    public String toString() {
        return String.format("Order #%d | %-10s | Rs.%-8.2f | %s",
                orderId, customerName, getFinalAmount(), status);
    }
}


/**
 * Multithreading: each agent is a Runnable that runs on its own thread,
 * picks orders from a shared BlockingQueue and "delivers" them.
 */
class DeliveryAgent implements Runnable {
    private final BlockingQueue<Order> queue;
    private final DeliveryService service;

    public DeliveryAgent(BlockingQueue<Order> queue, DeliveryService service) {
        this.queue = queue;
        this.service = service;
    }

    @Override
    public void run() {
        String agent = Thread.currentThread().getName();
        try {
            // keep working while service is running OR orders are still waiting
            while (service.isRunning() || !queue.isEmpty()) {
                Order order = queue.poll(500, TimeUnit.MILLISECONDS);
                if (order == null) continue;

                order.setStatus(OrderStatus.PREPARING);
                System.out.println("\n[" + agent + "] Preparing order #" + order.getOrderId());
                Thread.sleep(2000);

                order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
                System.out.println("[" + agent + "] Out for delivery: order #" + order.getOrderId());
                Thread.sleep(3000);

                order.setStatus(OrderStatus.DELIVERED);
                service.incrementDelivered();   // synchronized/atomic update
                System.out.println("[" + agent + "] DELIVERED order #" + order.getOrderId()
                        + " to " + order.getCustomerName());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[" + agent + "] interrupted.");
        }
    }
}


/**
 * Multithreading: ExecutorService thread pool with 3 delivery agents.
 * Collections: LinkedBlockingQueue (thread-safe Queue) holds pending orders.
 */
class DeliveryService {
    private static final int AGENTS = 3;

    private final BlockingQueue<Order> queue = new LinkedBlockingQueue<>();
    private final AtomicInteger deliveredCount = new AtomicInteger(0);
    private volatile boolean running = false;
    private ExecutorService pool;

    public void start() {
        running = true;
        pool = Executors.newFixedThreadPool(AGENTS, new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger(1);
            @Override public Thread newThread(Runnable r) {
                return new Thread(r, "Agent-" + n.getAndIncrement());
            }
        });
        for (int i = 0; i < AGENTS; i++) {
            pool.submit(new DeliveryAgent(queue, this));
        }
    }

    public void submitOrder(Order order) { queue.offer(order); }
    public boolean isRunning() { return running; }
    public void incrementDelivered() { deliveredCount.incrementAndGet(); }
    public int getDeliveredCount() { return deliveredCount.get(); }
    public int getPendingCount() { return queue.size(); }

    /** Waits for all pending deliveries to finish, then stops threads. */
    public void stop() {
        running = false;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}


/**
 * Collections: TreeMap (sorted menu), ArrayList (order history),
 * LinkedHashMap (cart), HashSet (unique customers).
 */
class FoodService {
    private final Map<Integer, MenuItem> menu = new TreeMap<>();
    private final List<Order> orderHistory = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> customers = new HashSet<>();

    public FoodService() {
        // Polymorphism: parent reference, child objects
        addItem(new VegItem(1, "Paneer Butter Masala", 220));
        addItem(new VegItem(2, "Veg Biryani", 180));
        addItem(new VegItem(3, "Margherita Pizza", 250));
        addItem(new NonVegItem(4, "Chicken Biryani", 260));
        addItem(new NonVegItem(5, "Butter Chicken", 300));
        addItem(new NonVegItem(6, "Fish Fry", 280));
        addItem(new BeverageItem(7, "Cold Coffee", 90));
        addItem(new BeverageItem(8, "Fresh Lime Soda", 60));
    }

    private void addItem(MenuItem item) { menu.put(item.getId(), item); }

    public void showMenu() {
        System.out.println("\n================= MENU =================");
        System.out.printf("%-4s %-22s %-10s %s%n", "ID", "Item", "Type", "Price");
        for (MenuItem item : menu.values()) System.out.println(item);
        System.out.println("========================================");
    }

    public void addToCart(Map<MenuItem, Integer> cart, int itemId, int qty)
            throws ItemNotFoundException, InvalidQuantityException {
        MenuItem item = menu.get(itemId);
        if (item == null) throw new ItemNotFoundException(itemId);
        if (qty < 1 || qty > 20) throw new InvalidQuantityException(qty);
        cart.merge(item, qty, Integer::sum);   // add qty if item already present
    }

    public Order createOrder(String customer, Map<MenuItem, Integer> cart) throws EmptyCartException {
        if (cart.isEmpty()) throw new EmptyCartException();
        Order order = new Order(customer, cart);
        orderHistory.add(order);
        customers.add(customer.toLowerCase());
        return order;
    }

    public void showOrders() {
        if (orderHistory.isEmpty()) {
            System.out.println("\nNo orders placed yet.");
            return;
        }
        System.out.println("\n---------------- ORDERS ----------------");
        synchronized (orderHistory) {
            for (Order o : orderHistory) System.out.println(o);
        }
    }

    public int getUniqueCustomerCount() { return customers.size(); }
    public int getTotalOrders() { return orderHistory.size(); }
}
