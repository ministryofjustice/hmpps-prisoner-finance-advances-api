package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val ROLE_PRISONER_FINANCE__ADVANCES__RO = "ROLE_PRISONER_FINANCE__ADVANCES__RO"
const val ROLE_PRISONER_FINANCE__ADVANCES__RW = "ROLE_PRISONER_FINANCE__ADVANCES__RW"
const val TAG_ADVANCES = "ADVANCES"

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version!!

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://prisoner-finance-advances-api-dev.hmpps.service.justice.gov.uk").description("Development"),
        Server().url("https://prisoner-finance-advances-api-preprod.hmpps.service.justice.gov.uk").description("Pre-Production"),
        Server().url("https://prisoner-finance-advances-api.hmpps.service.justice.gov.uk").description("Production"),
        Server().url("http://localhost:8080").description("Local"),
      ),
    )
    .info(
      Info().title("HMPPS Prisoner Finance Advances Api").version(version)
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk")),
    )
    .components(
      Components()
        .addSecuritySchemes(
          "bearer-jwt",
          SecurityScheme().addBearerJwtRequirement(listOf(ROLE_PRISONER_FINANCE__ADVANCES__RO, ROLE_PRISONER_FINANCE__ADVANCES__RW)),
        ),
    )
    .addSecurityItem(SecurityRequirement().addList("bearer-jwt", listOf("read", "write")))
    .tags(apiTags())

  private fun apiTags(): List<Tag> = listOf(
    Tag()
      .name(TAG_ADVANCES)
      .description("Endpoints for advance management and viewing in prisoner finance."),
  )
}

private fun SecurityScheme.addBearerJwtRequirement(roles: List<String>): SecurityScheme = type(SecurityScheme.Type.HTTP)
  .scheme("bearer")
  .bearerFormat("JWT")
  .`in`(SecurityScheme.In.HEADER)
  .name("Authorization")
  .description("A HMPPS Auth access token with either role: `$roles`")
