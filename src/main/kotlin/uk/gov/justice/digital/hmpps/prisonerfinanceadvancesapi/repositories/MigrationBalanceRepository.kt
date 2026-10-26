package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceBalanceMigrationEntity
import java.util.UUID

interface MigrationBalanceRepository : JpaRepository<AdvanceBalanceMigrationEntity, UUID>
