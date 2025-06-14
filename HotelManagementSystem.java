import javax.swing.*;
import java.awt.*;
import java.io.FileReader;
import java.sql.*;
import java.util.Properties;

public class HotelManagementSystem {

    public static void main(String[] args) {
        // Use SwingUtilities.invokeLater to ensure thread safety for the GUI
        SwingUtilities.invokeLater(() -> new LoginFrame());
    }

    /**
     * Database class handles all JDBC operations.
     */
    static class Database {

        // Method to connect to the database using credentials from a properties file
        private static Connection connect() throws Exception {
            Properties props = new Properties();
            props.load(new FileReader("config.properties"));
            return DriverManager.getConnection(
                    props.getProperty("db.url"),
                    props.getProperty("db.user"),
                    props.getProperty("db.password")
            );
        }

        // Authenticates a user against the database
        public static boolean authenticate(String user, String pass) {
            String sql = "SELECT * FROM users WHERE username=? AND password=?";
            try (Connection con = connect();
                 PreparedStatement st = con.prepareStatement(sql)) {
                st.setString(1, user);
                st.setString(2, pass);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next();
                }
            } catch (Exception e) {
                e.printStackTrace(); // Log error
                JOptionPane.showMessageDialog(null, "Database error during authentication.", "Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        // Registers a new user
        public static void register(String user, String pass) {
            String sql = "INSERT INTO users(username, password) VALUES(?, ?)";
            try (Connection con = connect();
                 PreparedStatement st = con.prepareStatement(sql)) {
                st.setString(1, user);
                st.setString(2, pass);
                st.executeUpdate();
                JOptionPane.showMessageDialog(null, "Registration successful!");
            } catch (SQLIntegrityConstraintViolationException e) {
                JOptionPane.showMessageDialog(null, "Username '" + user + "' already exists.", "Registration Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "An error occurred during registration.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        // Books a room for a user
        public static void bookRoom(String user, String type, int cost) {
            String sql = "INSERT INTO bookings(username, room_type, cost) VALUES(?, ?, ?)";
            try (Connection con = connect();
                 PreparedStatement st = con.prepareStatement(sql)) {
                st.setString(1, user);
                st.setString(2, type);
                st.setInt(3, cost);
                st.executeUpdate();
                JOptionPane.showMessageDialog(null, "Room booked successfully!");
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed to book room.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        // Places a food order for a user
        public static void orderFood(String user, int amount) {
            if (amount <= 0) {
                JOptionPane.showMessageDialog(null, "Please select at least one item.", "Order Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String sql = "INSERT INTO food_orders(username, amount) VALUES(?, ?)";
            try (Connection con = connect();
                 PreparedStatement st = con.prepareStatement(sql)) {
                st.setString(1, user);
                st.setInt(2, amount);
                st.executeUpdate();
                JOptionPane.showMessageDialog(null, "Food ordered successfully! Total: ₹" + amount);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed to place food order.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        // Calculates the total bill for a user
        public static void checkout(String user) {
            String roomSql = "SELECT SUM(cost) FROM bookings WHERE username=? AND paid=false";
            String foodSql = "SELECT SUM(amount) FROM food_orders WHERE username=? AND paid=false";
            int totalRoomBill = 0;
            int totalFoodBill = 0;

            try (Connection con = connect()) {
                // Calculate room bill
                try (PreparedStatement roomSt = con.prepareStatement(roomSql)) {
                    roomSt.setString(1, user);
                    try (ResultSet rs = roomSt.executeQuery()) {
                        if (rs.next()) {
                            totalRoomBill = rs.getInt(1);
                        }
                    }
                }
                // Calculate food bill
                try (PreparedStatement foodSt = con.prepareStatement(foodSql)) {
                    foodSt.setString(1, user);
                    try (ResultSet rs = foodSt.executeQuery()) {
                        if (rs.next()) {
                            totalFoodBill = rs.getInt(1);
                        }
                    }
                }
                int totalBill = totalRoomBill + totalFoodBill;
                if (totalBill > 0) {
                    String message = String.format("Checkout Bill:\n\nRoom Charges: ₹%d\nFood Charges: ₹%d\n\nTotal Due: ₹%d", totalRoomBill, totalFoodBill, totalBill);
                    JOptionPane.showMessageDialog(null, message, "Total Bill", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null, "No outstanding charges found for " + user, "Checkout", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed to calculate bill.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        // Marks all of a user's outstanding charges as paid
        public static void makePayment(String user) {
            String updateBookingsSql = "UPDATE bookings SET paid=true WHERE username=?";
            String updateFoodSql = "UPDATE food_orders SET paid=true WHERE username=?";
            try (Connection con = connect()) {
                con.setAutoCommit(false); // Start transaction
                try (PreparedStatement bookingSt = con.prepareStatement(updateBookingsSql);
                     PreparedStatement foodSt = con.prepareStatement(updateFoodSql)) {

                    bookingSt.setString(1, user);
                    int bookingsCleared = bookingSt.executeUpdate();

                    foodSt.setString(1, user);
                    int foodOrdersCleared = foodSt.executeUpdate();

                    con.commit(); // Commit transaction

                    if (bookingsCleared > 0 || foodOrdersCleared > 0) {
                        JOptionPane.showMessageDialog(null, "Payment successful. All dues cleared.", "Payment", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(null, "No outstanding payments to be made.", "Payment", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    con.rollback(); // Rollback transaction on error
                    throw e;
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Payment failed.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * LoginFrame provides the UI for user authentication.
     */
    static class LoginFrame extends JFrame {
        JTextField userField;
        JPasswordField passField;
        JButton loginBtn, registerBtn;

        public LoginFrame() {
            setTitle("Hotel Login");
            setSize(350, 220);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null); // Center the frame

            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0;
            gbc.gridy = 0;
            add(new JLabel("Username:"), gbc);

            gbc.gridx = 1;
            gbc.gridy = 0;
            userField = new JTextField(15);
            add(userField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            add(new JLabel("Password:"), gbc);

            gbc.gridx = 1;
            gbc.gridy = 1;
            passField = new JPasswordField(15);
            add(passField, gbc);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            loginBtn = new JButton("Login");
            registerBtn = new JButton("Register");
            buttonPanel.add(loginBtn);
            buttonPanel.add(registerBtn);

            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.CENTER;
            add(buttonPanel, gbc);

            loginBtn.addActionListener(e -> handleLogin());
            registerBtn.addActionListener(e -> handleRegister());

            setVisible(true);
        }

        private void handleLogin() {
            String user = userField.getText();
            String pass = new String(passField.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password cannot be empty.", "Input Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (Database.authenticate(user, pass)) {
                dispose();
                new DashboardFrame(user);
            } else {
                JOptionPane.showMessageDialog(this, "Invalid username or password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void handleRegister() {
            String user = userField.getText();
            String pass = new String(passField.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password cannot be empty.", "Input Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Database.register(user, pass);
        }
    }

    /**
     * DashboardFrame is the main menu after a successful login.
     */
    static class DashboardFrame extends JFrame {
        public DashboardFrame(String username) {
            setTitle("Dashboard - " + username);
            setSize(400, 300);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            setLayout(new GridLayout(2, 2, 10, 10));

            JButton bookRoom = new JButton("Book Room");
            JButton orderFood = new JButton("Order Food");
            JButton checkout = new JButton("View Bill / Checkout");
            JButton payment = new JButton("Make Payment");

            add(bookRoom);
            add(orderFood);
            add(checkout);
            add(payment);

            bookRoom.addActionListener(e -> new RoomBookingFrame(username));
            orderFood.addActionListener(e -> new FoodOrderFrame(username));
            checkout.addActionListener(e -> Database.checkout(username));
            payment.addActionListener(e -> Database.makePayment(username));

            ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            setVisible(true);
        }
    }

    /**
     * RoomBookingFrame provides the UI for booking a room.
     */
    static class RoomBookingFrame extends JFrame {
        private final String[] roomOptions = {"Single - ₹1500", "Double - ₹2500", "Suite - ₹4000"};
        private final int[] roomCosts = {1500, 2500, 4000};

        public RoomBookingFrame(String username) {
            setTitle("Book Room");
            setSize(350, 200);
            setLocationRelativeTo(null);

            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0;
            gbc.gridy = 0;
            add(new JLabel("Room Type:"), gbc);

            gbc.gridx = 1;
            gbc.gridy = 0;
            JComboBox<String> roomType = new JComboBox<>(roomOptions);
            add(roomType, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.CENTER;
            JButton confirm = new JButton("Confirm Booking");
            add(confirm, gbc);

            confirm.addActionListener(e -> {
                int selectedIndex = roomType.getSelectedIndex();
                String room = roomType.getSelectedItem().toString().split(" - ")[0];
                int cost = roomCosts[selectedIndex];
                Database.bookRoom(username, room, cost);
                dispose();
            });

            setVisible(true);
        }
    }

    /**
     * FoodOrderFrame provides the UI for ordering food.
     */
    static class FoodOrderFrame extends JFrame {
        public FoodOrderFrame(String username) {
            setTitle("Order Food");
            setSize(300, 250);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));

            JPanel itemsPanel = new JPanel();
            itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
            itemsPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

            JCheckBox pizza = new JCheckBox("Pizza - ₹150");
            JCheckBox burger = new JCheckBox("Burger - ₹100");
            JCheckBox tea = new JCheckBox("Tea - ₹50");

            itemsPanel.add(pizza);
            itemsPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            itemsPanel.add(burger);
            itemsPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            itemsPanel.add(tea);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton order = new JButton("Order Now");
            buttonPanel.add(order);

            add(itemsPanel, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH);

            order.addActionListener(e -> {
                int total = 0;
                if (pizza.isSelected()) total += 150;
                if (burger.isSelected()) total += 100;
                if (tea.isSelected()) total += 50;

                Database.orderFood(username, total);

                if (total > 0) {
                    dispose();
                }
            });

            setVisible(true);
        }
    }
}