package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrisonerFinanceAdvancesApi

fun main(args: Array<String>) {
  runApplication<PrisonerFinanceAdvancesApi>(*args)
}
