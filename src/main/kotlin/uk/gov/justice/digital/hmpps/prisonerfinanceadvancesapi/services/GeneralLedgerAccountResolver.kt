package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.exceptions.CustomException
import java.util.UUID

@Service
class GeneralLedgerAccountResolver(
  private val generalLedgerApi: GeneralLedgerApiClient,
) {
  fun resolveSubAccount(parentRef: String, subRef: String): UUID {
    val subAccount = generalLedgerApi.findSubAccount(
      parentReference = parentRef,
      subAccountReference = subRef,
    )

    if (subAccount == null) {
      throw CustomException("Sub account not found", HttpStatus.NOT_FOUND)
    }

    return subAccount.id
  }
}