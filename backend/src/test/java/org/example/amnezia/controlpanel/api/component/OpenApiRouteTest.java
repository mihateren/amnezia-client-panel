package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

class OpenApiRouteTest extends BaseComponentTest {
  @Autowired
  OpenApiRouteTest(
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
    this.mappings = mappings;
  }

  @Test
  void shouldUseGeneratedInterfacesAsTheOnlyControllerMappings() {
    var controllers =
        List.of(
            org.example.amnezia.controlpanel.api.controller.AuthController.class,
            org.example.amnezia.controlpanel.api.controller.InvitationController.class,
            org.example.amnezia.controlpanel.api.controller.DeviceController.class,
            org.example.amnezia.controlpanel.api.controller.ConfigController.class,
            org.example.amnezia.controlpanel.api.controller.AdminController.class,
            org.example.amnezia.controlpanel.api.controller.VpnServerController.class,
            org.example.amnezia.controlpanel.api.controller.VpnMigrationController.class,
            org.example.amnezia.controlpanel.api.controller.AuditController.class);
    controllers.forEach(
        controller -> {
          assertThat(controller.getInterfaces())
              .isNotEmpty()
              .allMatch(api -> api.getPackageName().endsWith(".rest.api"));
          assertThat(controller.getDeclaredAnnotations())
              .noneMatch(a -> a.annotationType().getSimpleName().endsWith("Mapping"));
          for (var method : controller.getDeclaredMethods()) {
            assertThat(method.getDeclaredAnnotations())
                .noneMatch(a -> a.annotationType().getSimpleName().endsWith("Mapping"));
            for (var parameter : method.getParameters())
              assertThat(parameter.getDeclaredAnnotations()).isEmpty();
          }
        });
  }

  private final RequestMappingHandlerMapping mappings;

  @Test
  void shouldImplementEveryApplicationRouteInOpenApi() throws Exception {
    var actual = new HashSet<String>();
    mappings
        .getHandlerMethods()
        .keySet()
        .forEach(
            info -> {
              for (var path : info.getPatternValues())
                for (var method : info.getMethodsCondition().getMethods())
                  actual.add(method.name() + " " + path);
            });
    try (var reader = Files.newBufferedReader(Path.of("docs-java/openapi/openapi.yaml"))) {
      Map<String, Object> spec = new Yaml().load(reader);
      var paths = (Map<String, Map<String, Object>>) spec.get("paths");
      paths.forEach(
          (path, operations) -> {
            if (!path.startsWith("/api/")) return;
            operations.keySet().stream()
                .filter(method -> Set.of("get", "post", "put", "patch", "delete").contains(method))
                .forEach(
                    method ->
                        assertThat(actual).contains(method.toUpperCase(Locale.ROOT) + " " + path));
          });
    }
  }
}
