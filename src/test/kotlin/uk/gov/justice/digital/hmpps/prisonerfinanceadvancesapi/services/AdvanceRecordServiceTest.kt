package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvancePaymentEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecordEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvancePaymentRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services.GeneralLedgerAccountResolver
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services.InMemoryAccountCache
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services.InsertService
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services.RecordService
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdvanceRecordServiceTest {

  @Mock
  lateinit var advanceRecordRepository: AdvanceRecordRepository

  @Mock
  lateinit var advancePaymentRepository: AdvancePaymentRepository

  @Mock
  lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @Mock
  lateinit var accountResolver: GeneralLedgerAccountResolver

  private lateinit var advanceRecordService: RecordService

  @BeforeEach
  fun setUp() {
    advanceRecordService = RecordService(
      advanceRecordRepository = advanceRecordRepository,
      advancePaymentRepository = advancePaymentRepository,
      insertService = InsertService(advanceRecordRepository, advancePaymentRepository),
      generalLedgerApiClient = generalLedgerApiClient,
      memoryAccountCache = InMemoryAccountCache(),
      accountResolver = accountResolver,
    )
  }

  @Nested
  inner class CreateAdvanceRecord {
    lateinit var advanceRecordResponse: AdvanceRecordResponse
    val idempotencyKey: UUID = UUID.randomUUID()
    val request = CreateAdvanceRecordRequest(
      legacyPaymentProfileId = 1,
      legacyInformationNumber = "123456",
      prisonNumber = "A123XZ",
      prisonID = "LEI",
      amount = 100,
      createdOn = Instant.now(),
      repaymentStartDate = Instant.now(),
      repaymentAmount = 100,
      reference = "",
      comment = "",
      createdBy = "TEST",
      status = AdvanceStatus.ACTIVE,
      legacyTransactionId = 1234,
    )
    val glTransactionId = UUID.randomUUID()
    val advanceEntity = AdvanceRecordEntity(
      legacyPaymentProfileId = request.legacyPaymentProfileId,
      legacyInformationNumber = request.legacyInformationNumber,
      prisonNumber = request.prisonNumber,
      prisonID = request.prisonID,
      amount = request.amount,
      createdOn = request.createdOn,
      repaymentStartDate = request.repaymentStartDate,
      repaymentAmount = request.repaymentAmount,
      reference = request.reference,
      comment = request.comment,
      createdBy = request.createdBy,
      status = request.status,
    )

    val advancePaymentEntity = AdvancePaymentEntity(
      advanceRecordId = advanceEntity.id,
      transactionId = glTransactionId,
      prisonerPostingType = PostingType.CR,
      amount = request.amount,
      timestamp = request.createdOn,
      createdBy = request.createdBy,
    )

    @Nested
    inner class AdvanceRecordCreated {
      val prisonSubAccount = UUID.randomUUID()
      val prisonerSubAccount = UUID.randomUUID()

      val glTransactionRequestCaptor = argumentCaptor<CreateTransactionRequest>()

      @BeforeEach
      fun setup() {
        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonID),
            subRef = eq("1502:ADV"),
            cache = any(),
          )
        }.thenReturn(prisonSubAccount)

        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonNumber),
            subRef = eq("SPENDS"),
            cache = any(),
          )
        }.thenReturn(prisonerSubAccount)

        whenever(
          generalLedgerApiClient.postTransaction(
            request = glTransactionRequestCaptor.capture(),
            idempotencyKey = eq(idempotencyKey),
          ),
        ).thenReturn(glTransactionId)

        val advanceEntity = AdvanceRecordEntity(
          legacyPaymentProfileId = request.legacyPaymentProfileId,
          legacyInformationNumber = request.legacyInformationNumber,
          prisonNumber = request.prisonNumber,
          prisonID = request.prisonID,
          amount = request.amount,
          createdOn = request.createdOn,
          repaymentStartDate = request.repaymentStartDate,
          repaymentAmount = request.repaymentAmount,
          reference = request.reference,
          comment = request.comment,
          createdBy = request.createdBy,
          status = request.status,
        )

        whenever(
          advanceRecordRepository.saveAndFlush(any<AdvanceRecordEntity>()),
        ).thenReturn(advanceEntity)

        whenever(
          advancePaymentRepository.saveAndFlush(any<AdvancePaymentEntity>()),
        ).thenReturn(advancePaymentEntity)

        advanceRecordResponse = advanceRecordService.createAdvanceRecord(
          createAdvanceRecordRequest = request,
          idempotencyKey = idempotencyKey,
        )
      }

      @Test
      fun `should post transaction to GL`() {
        verify(generalLedgerApiClient, times(1)).postTransaction(
          request = any(),
          idempotencyKey = eq(idempotencyKey),
        )

        val glRequest = glTransactionRequestCaptor.firstValue

        assertThat(glRequest.legacyTransactionId).isEqualTo(request.legacyTransactionId)
        assertThat(glRequest.amount).isEqualTo(request.amount)
        assertThat(glRequest.description).isEqualTo(request.comment)
        assertThat(glRequest.reference).isEqualTo(request.reference)
        assertThat(glRequest.timestamp).isEqualTo(request.createdOn)
        assertThat(glRequest.entrySequence).isEqualTo(1)
        assertThat(glRequest.postings).hasSize(2)

        val debitPosting = glRequest.postings.first { it.type == CreatePostingRequest.Type.DR }
        assertThat(debitPosting.entrySequence).isEqualTo(1)
        assertThat(debitPosting.amount).isEqualTo(request.amount)
        assertThat(debitPosting.subAccountId).isEqualTo(prisonSubAccount)

        val creditPosting = glRequest.postings.first { it.type == CreatePostingRequest.Type.CR }
        assertThat(creditPosting.entrySequence).isEqualTo(2)
        assertThat(creditPosting.amount).isEqualTo(request.amount)
        assertThat(creditPosting.subAccountId).isEqualTo(prisonerSubAccount)
      }

      @Test
      fun `should create an advance record`() {
        verify(advanceRecordRepository, times(1))
          .saveAndFlush(any<AdvanceRecordEntity>())

        verify(advancePaymentRepository, times(1))
          .saveAndFlush(any<AdvancePaymentEntity>())

        assertThat(advanceRecordResponse.id).isNotNull()
        assertThat(advanceRecordResponse.createdOn).isEqualTo(request.createdOn)
        assertThat(advanceRecordResponse.createdBy).isEqualTo(request.createdBy)
        assertThat(advanceRecordResponse.reference).isEqualTo(request.reference)
        assertThat(advanceRecordResponse.prisonNumber).isEqualTo(request.prisonNumber)
        assertThat(advanceRecordResponse.amount).isEqualTo(request.amount)
        assertThat(advanceRecordResponse.status).isEqualTo(request.status)
        assertThat(advanceRecordResponse.comment).isEqualTo(request.comment)
        assertThat(advanceRecordResponse.legacyInformationNumber).isEqualTo(request.legacyInformationNumber)
        assertThat(advanceRecordResponse.legacyPaymentProfileId).isEqualTo(request.legacyPaymentProfileId)
        assertThat(advanceRecordResponse.repaymentAmount).isEqualTo(request.repaymentAmount)
        assertThat(advanceRecordResponse.repaymentStartDate).isEqualTo(request.repaymentStartDate)
      }
    }

    @Nested
    inner class AdvanceRecordRepositoryThrowsDataIntegrityViolationException {
      val advanceEntity = AdvanceRecordEntity(
        legacyPaymentProfileId = request.legacyPaymentProfileId,
        legacyInformationNumber = request.legacyInformationNumber,
        prisonNumber = request.prisonNumber,
        prisonID = request.prisonID,
        amount = request.amount,
        createdOn = request.createdOn,
        repaymentStartDate = request.repaymentStartDate,
        repaymentAmount = request.repaymentAmount,
        reference = request.reference,
        comment = request.comment,
        createdBy = request.createdBy,
        status = request.status,
      )

      @BeforeEach
      fun setup() {
        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonID),
            subRef = eq("1502:ADV"),
            cache = any(),
          )
        }.thenReturn(UUID.randomUUID())

        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonNumber),
            subRef = eq("SPENDS"),
            cache = any(),
          )
        }.thenReturn(UUID.randomUUID())

        whenever(
          generalLedgerApiClient.postTransaction(
            request = any<CreateTransactionRequest>(),
            idempotencyKey = eq(idempotencyKey),
          ),
        ).thenReturn(glTransactionId)

        whenever {
          advanceRecordRepository.saveAndFlush(any<AdvanceRecordEntity>())
        }.thenThrow(DataIntegrityViolationException("uc_advance_records_legacy_payment_profile_id"))

        whenever {
          advanceRecordRepository.getAdvanceRecordsByLegacyPaymentProfileId(
            legacyPaymentProfileId = request.legacyPaymentProfileId,
          )
        }.thenReturn(advanceEntity)

        whenever {
          advancePaymentRepository.findByTransactionId(transactionId = glTransactionId)
        }.thenReturn(advancePaymentEntity)

        advanceRecordResponse = advanceRecordService.createAdvanceRecord(
          createAdvanceRecordRequest = request,
          idempotencyKey = idempotencyKey,
        )
      }

      @Test
      fun `should handle data integrity violation exception and return existing record`() {
        verify(advanceRecordRepository, times(1))
          .saveAndFlush(any<AdvanceRecordEntity>())

        verify(advanceRecordRepository, times(1))
          .getAdvanceRecordsByLegacyPaymentProfileId(request.legacyPaymentProfileId)

        assertThat(advanceRecordResponse.id).isEqualTo(advanceEntity.id)
        assertThat(advanceRecordResponse.createdOn).isEqualTo(request.createdOn)
        assertThat(advanceRecordResponse.createdBy).isEqualTo(request.createdBy)
        assertThat(advanceRecordResponse.reference).isEqualTo(request.reference)
        assertThat(advanceRecordResponse.prisonNumber).isEqualTo(request.prisonNumber)
        assertThat(advanceRecordResponse.amount).isEqualTo(request.amount)
        assertThat(advanceRecordResponse.status).isEqualTo(request.status)
        assertThat(advanceRecordResponse.comment).isEqualTo(request.comment)
        assertThat(advanceRecordResponse.legacyInformationNumber).isEqualTo(request.legacyInformationNumber)
        assertThat(advanceRecordResponse.legacyPaymentProfileId).isEqualTo(request.legacyPaymentProfileId)
        assertThat(advanceRecordResponse.repaymentAmount).isEqualTo(request.repaymentAmount)
        assertThat(advanceRecordResponse.repaymentStartDate).isEqualTo(request.repaymentStartDate)
      }
    }

    @Nested
    inner class AdvancePaymentRepositoryThrowsDataIntegrityViolationException {
      @BeforeEach
      fun setup() {
        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonID),
            subRef = eq("1502:ADV"),
            cache = any(),
          )
        }.thenReturn(UUID.randomUUID())

        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonNumber),
            subRef = eq("SPENDS"),
            cache = any(),
          )
        }.thenReturn(UUID.randomUUID())

        whenever(
          generalLedgerApiClient.postTransaction(
            request = any<CreateTransactionRequest>(),
            idempotencyKey = eq(idempotencyKey),
          ),
        ).thenReturn(glTransactionId)

        whenever {
          advanceRecordRepository.saveAndFlush(any<AdvanceRecordEntity>())
        }.thenReturn(advanceEntity)

        whenever {
          advanceRecordRepository.getAdvanceRecordsByLegacyPaymentProfileId(request.legacyPaymentProfileId)
        }.thenReturn(advanceEntity)

        whenever {
          advancePaymentRepository.saveAndFlush(any<AdvancePaymentEntity>())
        }.thenThrow(DataIntegrityViolationException("uc_advance_record_payments_transaction_id"))

        whenever(
          advancePaymentRepository.findByTransactionId(
            transactionId = glTransactionId,
          ),
        ).thenReturn(advancePaymentEntity)

        advanceRecordResponse = advanceRecordService.createAdvanceRecord(
          createAdvanceRecordRequest = request,
          idempotencyKey = idempotencyKey,
        )
      }

      @Test
      fun `should handle data integrity violation exception and return existing record`() {
        verify(advanceRecordRepository, times(1))
          .saveAndFlush(any<AdvanceRecordEntity>())

        verify(advancePaymentRepository, times(1))
          .saveAndFlush(any<AdvancePaymentEntity>())

        verify(advancePaymentRepository, times(1))
          .findByTransactionId(eq(glTransactionId))

        assertThat(advanceRecordResponse.id).isEqualTo(advanceEntity.id)
        assertThat(advanceRecordResponse.createdOn).isEqualTo(request.createdOn)
        assertThat(advanceRecordResponse.createdBy).isEqualTo(request.createdBy)
        assertThat(advanceRecordResponse.reference).isEqualTo(request.reference)
        assertThat(advanceRecordResponse.prisonNumber).isEqualTo(request.prisonNumber)
        assertThat(advanceRecordResponse.amount).isEqualTo(request.amount)
        assertThat(advanceRecordResponse.status).isEqualTo(request.status)
        assertThat(advanceRecordResponse.comment).isEqualTo(request.comment)
        assertThat(advanceRecordResponse.legacyInformationNumber).isEqualTo(request.legacyInformationNumber)
        assertThat(advanceRecordResponse.legacyPaymentProfileId).isEqualTo(request.legacyPaymentProfileId)
        assertThat(advanceRecordResponse.repaymentAmount).isEqualTo(request.repaymentAmount)
        assertThat(advanceRecordResponse.repaymentStartDate).isEqualTo(request.repaymentStartDate)
      }
    }
  }

  @Nested
  inner class RepayAdvance {

    val advanceRepaymentRequest = CreateAdvanceRepaymentRequest(
      amount = 11,
      legacyTransactionId = 123,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
    )
    val prisonID = "LEI"
    val prisonNumber = "A123DX"
    val advanceAmount = 10000L
    val advanceId = UUID.randomUUID()
    val idempotency = UUID.randomUUID()
    val transactionToGl = argumentCaptor<CreateTransactionRequest>()
    val savedEntity = argumentCaptor<AdvancePaymentEntity>()

    val glTransactionId = UUID.randomUUID()
    val prisonSubAccount = UUID.randomUUID()
    val prisonerSubAccount = UUID.randomUUID()

    lateinit var advanceRepaymentResponse: AdvanceRepaymentResponse

    @BeforeEach
    fun setup() {
      val advanceEntityTime = Instant.now()
      whenever { advanceRecordRepository.getAdvanceRecordEntityById(advanceId) }.thenReturn(
        AdvanceRecordEntity(
          id = advanceId,
          legacyPaymentProfileId = 123,
          legacyInformationNumber = "12323",
          prisonNumber = prisonNumber,
          prisonID = prisonID,
          amount = advanceAmount,
          createdOn = advanceEntityTime,
          repaymentStartDate = advanceEntityTime,
          repaymentAmount = 11,
          reference = "test",
          comment = "test",
          createdBy = "TEST",
          status = AdvanceStatus.ACTIVE,
        ),
      )

      whenever {
        accountResolver.resolveSubAccount(
          parentRef = eq(prisonID),
          subRef = eq("1502:ADV"),
          cache = any(),
        )
      }.thenReturn(prisonSubAccount)

      whenever {
        accountResolver.resolveSubAccount(
          parentRef = eq(prisonNumber),
          subRef = eq("SPENDS"),
          cache = any(),
        )
      }.thenReturn(prisonerSubAccount)

      whenever {
        generalLedgerApiClient.postTransaction(
          request = transactionToGl.capture(),
          idempotencyKey = eq(idempotency),
        )
      }.thenReturn(glTransactionId)

      whenever(
        advancePaymentRepository.save(savedEntity.capture()),
      ).thenAnswer { it.arguments[0] }

      advanceRepaymentResponse = advanceRecordService.repayAdvance(
        advanceRepaymentRequest,
        advanceId = advanceId,
        idempotencyKey = idempotency,
      )
    }

    @Test
    fun `should create a transaction in GL for the advance repayment`() {
      val glRequest = transactionToGl.firstValue

      assertThat(glRequest.reference).isEqualTo("")
      assertThat(glRequest.amount).isEqualTo(advanceRepaymentRequest.amount)
      assertThat(glRequest.entrySequence).isEqualTo(1)
      assertThat(glRequest.legacyTransactionId).isEqualTo(advanceRepaymentRequest.legacyTransactionId)
      assertThat(glRequest.timestamp).isEqualTo(advanceRepaymentRequest.createdAt)
      assertThat(glRequest.description).isEqualTo(advanceRepaymentRequest.description)
      assertThat(glRequest.postings).hasSize(2)

      val debitPosting = glRequest.postings.first { it.type == CreatePostingRequest.Type.DR }
      assertThat(debitPosting.entrySequence).isEqualTo(1)
      assertThat(debitPosting.amount).isEqualTo(advanceRepaymentRequest.amount)
      assertThat(debitPosting.subAccountId).isEqualTo(prisonerSubAccount)

      val creditPosting = glRequest.postings.first { it.type == CreatePostingRequest.Type.CR }
      assertThat(creditPosting.entrySequence).isEqualTo(2)
      assertThat(creditPosting.amount).isEqualTo(advanceRepaymentRequest.amount)
      assertThat(creditPosting.subAccountId).isEqualTo(prisonSubAccount)
    }

    @Test
    fun `should save the advance repayment to the repository`() {
      val savedAdvanceRepayment = savedEntity.firstValue
      verify(
        advancePaymentRepository,
        times(1),
      ).save(any<AdvancePaymentEntity>())

      assertThat(advanceRepaymentResponse.id).isEqualTo(savedAdvanceRepayment.id)
      assertThat(savedAdvanceRepayment.prisonerPostingType).isEqualTo(PostingType.DR)

      assertThat(advanceRepaymentResponse.advanceId).isEqualTo(advanceId)
      assertThat(advanceRepaymentResponse.legacyTransactionId).isEqualTo(advanceRepaymentRequest.legacyTransactionId)
      assertThat(advanceRepaymentResponse.amount).isEqualTo(advanceRepaymentRequest.amount)
      assertThat(advanceRepaymentResponse.createdAt).isEqualTo(advanceRepaymentRequest.createdAt)
      assertThat(advanceRepaymentResponse.createdBy).isEqualTo(advanceRepaymentRequest.createdBy)
      assertThat(advanceRepaymentResponse.transactionId).isEqualTo(glTransactionId)
    }
  }
}
