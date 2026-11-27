import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Демонстрация поиска элементов интерфейса по шаблону OpenCV.
 * Приложение запускает небольшое окно калькулятора, сохраняет изображения
 * его кнопок и нажимает кнопки через Robot после их поиска на экране.
 */
public class Main {
    private static final double MATCH_ACCURACY = 0.98;
    private static final File TEMPLATE_DIR = new File("templates");

    private static JFrame frame;
    private static JLabel display;
    private static final Map<String, JButton> buttons = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        OpenCV.loadLocally();

        CalculatorModel model = new CalculatorModel();
        SwingUtilities.invokeAndWait(() -> createCalculator(model));
        waitForWindow();

        TEMPLATE_DIR.mkdirs();
        captureButtonTemplate("1");
        captureButtonTemplate("2");
        captureButtonTemplate("3");
        captureButtonTemplate("4");
        captureButtonTemplate("+");
        captureButtonTemplate("=");
        captureButtonTemplate("C");

        Rectangle calculatorArea = frame.getBounds();
        System.out.println("Калькулятор открыт. Область поиска: " + calculatorArea);

        runScenario("Проверка правильного результата", new String[]{"1", "2", "+", "3", "="}, "15", calculatorArea);
        runScenario("Проверка намеренно неверного ожидания", new String[]{"1", "2", "+", "4", "="}, "15", calculatorArea);

        System.out.println("\nОба сценария завершены. Закройте окно калькулятора.");
    }

    private static void createCalculator(CalculatorModel model) {
        frame = new JFrame("Калькулятор — OpenCV template matching");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        display = new JLabel("0", SwingConstants.RIGHT);
        display.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        display.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        frame.add(display, BorderLayout.NORTH);

        JPanel panel = new JPanel(new GridLayout(2, 4, 6, 6));
        addButton(panel, "1", model);
        addButton(panel, "2", model);
        addButton(panel, "3", model);
        addButton(panel, "4", model);
        addButton(panel, "+", model);
        addButton(panel, "=", model);
        addButton(panel, "C", model);

        frame.add(panel, BorderLayout.CENTER);
        frame.setSize(430, 260);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
    }

    private static void addButton(JPanel panel, String text, CalculatorModel model) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        button.addActionListener(event -> {
            model.press(text);
            display.setText(model.getDisplay());
        });
        buttons.put(text, button);
        panel.add(button);
    }

    private static void waitForWindow() throws InterruptedException {
        Thread.sleep(900);
    }

    private static void captureButtonTemplate(String buttonName) throws Exception {
        JButton button = buttons.get(buttonName);
        Point location = button.getLocationOnScreen();
        Rectangle bounds = new Rectangle(location.x, location.y, button.getWidth(), button.getHeight());
        BufferedImage image = new Robot().createScreenCapture(bounds);
        String fileName = templatePath(buttonName);
        ImageIO.write(image, "png", new File(fileName));
        System.out.println("Создан шаблон кнопки '" + buttonName + "': " + fileName);
    }

    private static String templatePath(String buttonName) {
        String fileName = buttonName.equals("+") ? "plus" : buttonName.equals("=") ? "equals"
                : buttonName.equals("C") ? "clear" : buttonName;
        return new File(TEMPLATE_DIR, fileName + ".png").getPath();
    }

    private static void runScenario(String title, String[] sequence, String expected, Rectangle area) throws Exception {
        System.out.println("\n" + title);
        clickTemplate("C", area);
        for (String key : sequence) {
            clickTemplate(key, area);
        }

        String actual = readDisplay();
        boolean passed = expected.equals(actual);
        System.out.printf("Ожидалось: %s | Получено: %s | Результат: %s%n",
                expected, actual, passed ? "УСПЕХ" : "НЕСОВПАДЕНИЕ");

        if (title.contains("правильного") && !passed) {
            throw new AssertionError("Правильный сценарий завершился неверно: " + actual);
        }
        if (title.contains("неверного ожидания") && passed) {
            throw new AssertionError("Негативный сценарий должен был обнаружить несовпадение.");
        }
    }

    private static void clickTemplate(String buttonName, Rectangle area) throws Exception {
        Rect found = find(templatePath(buttonName), MATCH_ACCURACY, area);
        click(found.Center());
        Thread.sleep(180);
    }

    private static String readDisplay() throws Exception {
        final String[] value = new String[1];
        SwingUtilities.invokeAndWait(() -> value[0] = display.getText());
        return value[0];
    }

    public static void move(Point point) throws AWTException {
        new Robot().mouseMove(point.x, point.y);
    }

    public static void click(Point point) throws AWTException {
        Robot robot = new Robot();
        move(point);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void enter_text(String keys) throws AWTException {
        Robot robot = new Robot();
        for (char character : keys.toCharArray()) {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(character);
            if (keyCode == KeyEvent.VK_UNDEFINED) {
                throw new IllegalArgumentException("Не найден код клавиши для символа: " + character);
            }
            robot.keyPress(keyCode);
            robot.delay(80);
            robot.keyRelease(keyCode);
            robot.delay(80);
        }
    }

    /**
     * Ищет изображение на экране. accuracy — минимальная точность совпадения
     * от 0.0 до 1.0; searchArea — область экрана или null для всего экрана.
     */
    public static Rect find(String templateFile, double accuracy, Rectangle searchArea) throws Exception {
        if (accuracy < 0.0 || accuracy > 1.0) {
            throw new IllegalArgumentException("Точность должна находиться в диапазоне от 0.0 до 1.0");
        }

        Rectangle captureArea = searchArea == null
                ? new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())
                : new Rectangle(searchArea);

        if (captureArea.width <= 0 || captureArea.height <= 0) {
            throw new IllegalArgumentException("Область поиска должна иметь положительную ширину и высоту");
        }

        BufferedImage screenshot = new Robot().createScreenCapture(captureArea);
        File screenshotFile = new File("screen.png");
        ImageIO.write(screenshot, "png", screenshotFile);

        Mat source = Imgcodecs.imread(screenshotFile.getAbsolutePath());
        Mat template = Imgcodecs.imread(new File(templateFile).getAbsolutePath());

        if (source.empty()) {
            throw new IllegalArgumentException("Не удалось загрузить снимок экрана: " + screenshotFile.getPath());
        }
        if (template.empty()) {
            throw new IllegalArgumentException("Не удалось загрузить шаблон: " + templateFile);
        }
        if (template.cols() > source.cols() || template.rows() > source.rows()) {
            throw new IllegalArgumentException("Шаблон больше области поиска: " + templateFile);
        }

        Mat result = new Mat();
        Imgproc.matchTemplate(source, template, result, Imgproc.TM_CCOEFF_NORMED);
        Core.MinMaxLocResult match = Core.minMaxLoc(result);

        source.release();
        template.release();
        result.release();

        if (match.maxVal < accuracy) {
            throw new Exception(String.format("%s не найден. Лучшее совпадение: %.2f%% (требуется %.2f%%)",
                    templateFile, match.maxVal * 100, accuracy * 100));
        }

        int screenX = captureArea.x + (int) Math.round(match.maxLoc.x);
        int screenY = captureArea.y + (int) Math.round(match.maxLoc.y);
        return new Rect(screenX, screenY, (int) matchSize(templateFile, true), (int) matchSize(templateFile, false));
    }

    private static double matchSize(String templateFile, boolean width) {
        Mat template = Imgcodecs.imread(new File(templateFile).getAbsolutePath());
        double size = width ? template.cols() : template.rows();
        template.release();
        return size;
    }

    private static class CalculatorModel {
        private String displayValue = "0";
        private double firstOperand;
        private boolean waitingForSecondOperand;
        private boolean showResult;

        void press(String key) {
            if (key.equals("C")) {
                displayValue = "0";
                firstOperand = 0;
                waitingForSecondOperand = false;
                showResult = false;
            } else if (key.matches("\\d")) {
                if (waitingForSecondOperand || showResult || displayValue.equals("0")) {
                    displayValue = key;
                    waitingForSecondOperand = false;
                    showResult = false;
                } else {
                    displayValue += key;
                }
            } else if (key.equals("+")) {
                firstOperand = Double.parseDouble(displayValue);
                waitingForSecondOperand = true;
                showResult = false;
            } else if (key.equals("=")) {
                double result = firstOperand + Double.parseDouble(displayValue);
                displayValue = result == Math.rint(result) ? Long.toString((long) result) : Double.toString(result);
                showResult = true;
                waitingForSecondOperand = false;
            }
        }

        String getDisplay() {
            return displayValue;
        }
    }
}
