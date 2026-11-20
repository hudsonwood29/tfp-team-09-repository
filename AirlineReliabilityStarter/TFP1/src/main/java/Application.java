import com.github.mvysny.vaadinboot.VaadinBoot;

/**
 * Starts the website. Click the green arrow next to main, then open
 * http://localhost:8080 (the browser usually opens by itself).
 */
public class Application {

    // AI helped me use Vaadin Boot, which starts a small web server
    // from main, so the project does not need Spring Boot or a package.
    public static void main(String[] args) throws Exception {
        new VaadinBoot().run();
    }
}
