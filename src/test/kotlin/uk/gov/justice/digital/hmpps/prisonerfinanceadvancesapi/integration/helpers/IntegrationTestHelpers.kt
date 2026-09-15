package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.helpers

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository

@TestConfiguration
class IntegrationTestHelpers(
  @Autowired
  private val advanceRecordRepository: AdvanceRecordRepository,
) {

  @Autowired
  lateinit var entityManager: EntityManager

  @Transactional
  fun clearDB() {
    entityManager.clear()
    entityManager.flush()
    advanceRecordRepository.deleteAll()
  }
}
