package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.exceptions.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvancePaymentEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecordEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceWriteOffEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.AdvanceWriteOffRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvancePaymentRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceWriteOffRepository
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdvanceRecordServiceTest {
  @Mock
  lateinit var advanceRecordRepository: AdvanceRecordRepository

  @Mock
  lateinit var advancePaymentRepository: AdvancePaymentRepository

  @Mock
  lateinit var advanceWriteOffRepository: AdvanceWriteOffRepository

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
      accountResolver = accountResolver,
      advanceWriteOffRepository = advanceWriteOffRepository,
    )
  }

  val prisonID = "LEI"
  val prisonNumber = "A123DX"
  val advanceAmount = 10000L
  val advanceId = UUID.randomUUID()
  val advanceEntityTime = Instant.now()
  val advanceEntity = AdvanceRecordEntity(
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
    updatedAt = null,
  )

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
      updatedAt = null,
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
          )
        }.thenReturn(prisonSubAccount)

        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonNumber),
            subRef = eq("SPENDS"),
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
          updatedAt = null,
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
        updatedAt = null,
      )

      @BeforeEach
      fun setup() {
        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonID),
            subRef = eq("1502:ADV"),
          )
        }.thenReturn(UUID.randomUUID())

        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonNumber),
            subRef = eq("SPENDS"),
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
          )
        }.thenReturn(UUID.randomUUID())

        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(request.prisonNumber),
            subRef = eq("SPENDS"),
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
      entrySequence = 2,
    )
    val prisonID = "LEI"
    val prisonNumber = "A123DX"
    val advanceAmount = 10000L
    val advanceId = UUID.randomUUID()
    val idempotency = UUID.randomUUID()
    val transactionToGl = argumentCaptor<CreateTransactionRequest>()
    val savedPaymentEntity = argumentCaptor<AdvancePaymentEntity>()
    val savedAdvanceEntity = argumentCaptor<AdvanceRecordEntity>()

    val glTransactionId = UUID.randomUUID()
    val prisonSubAccount = UUID.randomUUID()
    val prisonerSubAccount = UUID.randomUUID()

    lateinit var advanceRepaymentResponse: AdvanceRepaymentResponse

    val advanceEntityTime = Instant.now()
    val advanceEntity = AdvanceRecordEntity(
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
      updatedAt = null,
    )

    val advancePaymentToPrisonerEntity = AdvancePaymentEntity(
      advanceRecordId = advanceEntity.id,
      transactionId = UUID.randomUUID(),
      prisonerPostingType = PostingType.CR,
      amount = advanceAmount,
      timestamp = Instant.now(),
      createdBy = "TEST",
    )

    @Nested
    inner class SuccessfulCases {

      @BeforeEach
      fun setup() {
        whenever { advanceRecordRepository.getAdvanceRecordEntityById(advanceId) }.thenReturn(
          advanceEntity,
        )

        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(prisonID),
            subRef = eq("1502:ADV"),
          )
        }.thenReturn(prisonSubAccount)

        whenever {
          accountResolver.resolveSubAccount(
            parentRef = eq(prisonNumber),
            subRef = eq("SPENDS"),
          )
        }.thenReturn(prisonerSubAccount)

        whenever {
          generalLedgerApiClient.postTransaction(
            request = transactionToGl.capture(),
            idempotencyKey = eq(idempotency),
          )
        }.thenReturn(glTransactionId)

        whenever(
          advancePaymentRepository.saveAndFlush(savedPaymentEntity.capture()),
        ).thenAnswer { it.arguments[0] }

        whenever(
          advanceRecordRepository.saveAndFlush(savedAdvanceEntity.capture()),
        ).thenAnswer { it.arguments[0] }
      }

      @Test
      fun `should create a transaction in GL for the advance repayment`() {
        whenever(
          advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
        ).thenReturn(listOf(advancePaymentToPrisonerEntity))

        advanceRepaymentResponse = advanceRecordService.repayAdvance(
          advanceRepaymentRequest,
          advanceId = advanceId,
          idempotencyKey = idempotency,
        )
        val glRequest = transactionToGl.firstValue

        assertThat(glRequest.reference).isEqualTo("")
        assertThat(glRequest.amount).isEqualTo(advanceRepaymentRequest.amount)
        assertThat(glRequest.entrySequence).isEqualTo(2)
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
        whenever(
          advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
        ).thenReturn(listOf(advancePaymentToPrisonerEntity))

        advanceRepaymentResponse = advanceRecordService.repayAdvance(
          advanceRepaymentRequest,
          advanceId = advanceId,
          idempotencyKey = idempotency,
        )
        val savedAdvanceRepayment = savedPaymentEntity.firstValue
        verify(
          advancePaymentRepository,
          times(1),
        ).saveAndFlush(any<AdvancePaymentEntity>())

        assertThat(advanceRepaymentResponse.id).isEqualTo(savedAdvanceRepayment.id)
        assertThat(savedAdvanceRepayment.prisonerPostingType).isEqualTo(PostingType.DR)

        assertThat(advanceRepaymentResponse.advanceId).isEqualTo(advanceId)
        assertThat(advanceRepaymentResponse.legacyTransactionId).isEqualTo(advanceRepaymentRequest.legacyTransactionId)
        assertThat(advanceRepaymentResponse.amount).isEqualTo(advanceRepaymentRequest.amount)
        assertThat(advanceRepaymentResponse.createdAt).isEqualTo(advanceRepaymentRequest.createdAt)
        assertThat(advanceRepaymentResponse.createdBy).isEqualTo(advanceRepaymentRequest.createdBy)
        assertThat(advanceRepaymentResponse.transactionId).isEqualTo(glTransactionId)
      }

      @Test
      fun `should update the advance when a repayment zeros the balance`() {
        val advanceRepayment = AdvancePaymentEntity(
          advanceRecordId = advanceEntity.id,
          transactionId = UUID.randomUUID(),
          prisonerPostingType = PostingType.DR,
          amount = advanceAmount / 2,
          timestamp = Instant.now(),
          createdBy = "TEST",
        )

        whenever(
          advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
        ).thenReturn(listOf(advancePaymentToPrisonerEntity, advanceRepayment))

        val advanceRepaymentRequest = CreateAdvanceRepaymentRequest(
          amount = advanceAmount / 2,
          legacyTransactionId = 123,
          createdAt = Instant.now(),
          createdBy = "TEST",
          description = "Test description",
          entrySequence = 2,
        )

        advanceRepaymentResponse = advanceRecordService.repayAdvance(
          advanceRepaymentRequest,
          advanceId = advanceId,
          idempotencyKey = idempotency,
        )

        verify(
          advancePaymentRepository,
          times(1),
        ).findAdvancePaymentEntitiesByAdvanceRecordId(advanceId)

        verify(advanceRecordRepository, times(1)).saveAndFlush(any<AdvanceRecordEntity>())

        val updatedAdvance = savedAdvanceEntity.firstValue

        assertThat(updatedAdvance.id).isEqualTo(advanceId)
        assertThat(updatedAdvance.status).isEqualTo(AdvanceStatus.REPAID)
        assertThat(updatedAdvance.updatedAt).isNotNull()
      }

      @Test
      fun `should not update the advance when a repayment does not zero the balance`() {
        whenever(
          advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
        ).thenReturn(listOf(advancePaymentToPrisonerEntity))

        val advanceRepaymentRequest = CreateAdvanceRepaymentRequest(
          amount = 1,
          legacyTransactionId = 123,
          createdAt = Instant.now(),
          createdBy = "TEST",
          description = "Test description",
          entrySequence = 2,
        )

        advanceRepaymentResponse = advanceRecordService.repayAdvance(
          advanceRepaymentRequest,
          advanceId = advanceId,
          idempotencyKey = idempotency,
        )

        verify(
          advancePaymentRepository,
          times(1),
        ).findAdvancePaymentEntitiesByAdvanceRecordId(advanceId)

        verify(advanceRecordRepository, times(1)).saveAndFlush(any<AdvanceRecordEntity>())

        val updatedAdvance = savedAdvanceEntity.firstValue

        assertThat(updatedAdvance.id).isEqualTo(advanceId)
        assertThat(updatedAdvance.status).isEqualTo(AdvanceStatus.ACTIVE)
        assertThat(updatedAdvance.updatedAt).isNull()
      }

      @Test
      fun `should return the transaction without throwing if it already exists in the mapping table`() {
        val legacyTransactionId = 123L

        val advanceRepaymentRequest = CreateAdvanceRepaymentRequest(
          amount = 1,
          legacyTransactionId = legacyTransactionId,
          createdAt = Instant.now(),
          createdBy = "TEST",
          description = "Test description",
          entrySequence = 2,
        )

        val glUUID = UUID.randomUUID()

        val advancePaymentEntity = AdvancePaymentEntity(
          id = UUID.randomUUID(),
          advanceRecordId = advanceEntity.id,
          transactionId = glUUID,
          prisonerPostingType = PostingType.DR,
          amount = advanceRepaymentRequest.amount,
          timestamp = advanceRepaymentRequest.createdAt,
          createdBy = advanceRepaymentRequest.createdBy,
        )

        whenever(advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceId))
          .thenReturn(listOf(advancePaymentToPrisonerEntity))

        whenever(
          advanceRecordService.postAdvanceRepaymentTransaction(
            advanceRepaymentRequest,
            prisonID,
            prisonNumber,
            idempotency,
          ),
        ).thenAnswer(Answer { glUUID })

        whenever(advanceRecordRepository.saveAndFlush(savedAdvanceEntity.capture()))
          .thenAnswer { it.arguments[0] }

        whenever(
          advancePaymentRepository.saveAndFlush<AdvancePaymentEntity>(
            argThat { this.transactionId == glUUID },
          ),
        )
          .thenThrow(DataIntegrityViolationException("uc_advance_record_payments_transaction_id"))

        whenever(advancePaymentRepository.findByTransactionId(glUUID)).thenReturn(advancePaymentEntity)

        assertDoesNotThrow {
          advanceRecordService.repayAdvance(
            advanceRepaymentRequest,
            advanceId = advanceId,
            idempotencyKey = idempotency,
          )
        }

        val savedRepayment = advanceRecordService.repayAdvance(
          advanceRepaymentRequest,
          advanceId = advanceId,
          idempotencyKey = idempotency,
        )

        assertThat(savedRepayment.transactionId).isEqualTo(glUUID)
      }
    }

    @Nested
    inner class RepayAdvanceErrors {
      @Test
      fun `should throw a bad request exception when the repayment is greater than the outstanding balance`() {
        whenever { advanceRecordRepository.getAdvanceRecordEntityById(advanceId) }.thenReturn(
          advanceEntity,
        )

        whenever(
          advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
        ).thenReturn(listOf(advancePaymentToPrisonerEntity))

        val advanceRepaymentRequest = CreateAdvanceRepaymentRequest(
          amount = 999999999,
          legacyTransactionId = 123,
          createdAt = Instant.now(),
          createdBy = "TEST",
          description = "Test description",
          entrySequence = 2,
        )

        val exception = assertThrows<CustomException> {
          advanceRecordService.repayAdvance(
            advanceRepaymentRequest,
            advanceId = advanceId,
            idempotencyKey = idempotency,
          )
        }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.message).isEqualTo("Repayments cannot exceed the balance of the advance record")
      }

      @Test
      fun `should throw a bad request exception when the advance has a status of REPAID`() {
        val repaidAdvance = AdvanceRecordEntity(
          legacyPaymentProfileId = 123,
          legacyInformationNumber = "123",
          prisonNumber = "A231HX",
          prisonID = "LEI",
          amount = 123,
          createdOn = Instant.now(),
          repaymentStartDate = Instant.now(),
          repaymentAmount = 1,
          reference = "",
          comment = "",
          createdBy = "TEST",
          updatedAt = null,
          status = AdvanceStatus.REPAID,
        )

        whenever { advanceRecordRepository.getAdvanceRecordEntityById(repaidAdvance.id) }.thenReturn(
          repaidAdvance,
        )

        val advanceRepaymentRequest = CreateAdvanceRepaymentRequest(
          amount = 11,
          legacyTransactionId = 123,
          createdAt = Instant.now(),
          createdBy = "TEST",
          description = "Test description",
          entrySequence = 2,
        )

        val exception = assertThrows<CustomException> {
          advanceRecordService.repayAdvance(
            advanceRepaymentRequest,
            advanceId = repaidAdvance.id,
            idempotencyKey = idempotency,
          )
        }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.message).isEqualTo("Cannot repay an advance with the status of REPAID")
      }

      @Test
      fun `should throw a bad request exception when the advance has a status of WRITTEN_OFF`() {
        val repaidAdvance = AdvanceRecordEntity(
          legacyPaymentProfileId = 123,
          legacyInformationNumber = "123",
          prisonNumber = "A231HX",
          prisonID = "LEI",
          amount = 123,
          createdOn = Instant.now(),
          repaymentStartDate = Instant.now(),
          repaymentAmount = 1,
          reference = "",
          comment = "",
          createdBy = "TEST",
          updatedAt = null,
          status = AdvanceStatus.WRITTEN_OFF,
        )

        whenever { advanceRecordRepository.getAdvanceRecordEntityById(repaidAdvance.id) }.thenReturn(
          repaidAdvance,
        )

        val advanceRepaymentRequest = CreateAdvanceRepaymentRequest(
          amount = 11,
          legacyTransactionId = 123,
          createdAt = Instant.now(),
          createdBy = "TEST",
          description = "Test description",
          entrySequence = 2,
        )

        val exception = assertThrows<CustomException> {
          advanceRecordService.repayAdvance(
            advanceRepaymentRequest,
            advanceId = repaidAdvance.id,
            idempotencyKey = idempotency,
          )
        }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.message).isEqualTo("Cannot repay an advance with the status of WRITTEN_OFF")
      }
    }
  }

  @Nested
  inner class CalculateAdvanceBalance {
    val advanceEntity = AdvanceRecordEntity(
      legacyPaymentProfileId = 123,
      legacyInformationNumber = "123312",
      prisonNumber = "A123XS",
      prisonID = "LEI",
      amount = 100,
      createdOn = Instant.now(),
      repaymentStartDate = Instant.now(),
      repaymentAmount = 1,
      reference = "",
      comment = "",
      createdBy = "TEST",
      status = AdvanceStatus.ACTIVE,
      updatedAt = null,
    )

    val advancePaymentCreditToPrisoner = AdvancePaymentEntity(
      advanceRecordId = advanceEntity.id,
      transactionId = UUID.randomUUID(),
      prisonerPostingType = PostingType.CR,
      amount = advanceEntity.amount,
      timestamp = Instant.now(),
      createdBy = "TEST",
    )

    @Test
    fun `should calculate the advance balance when the advance doesn't have any repayments`() {
      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner))

      val accountBalance = advanceRecordService.calculateAdvanceBalance(advanceEntity)

      assertThat(accountBalance).isEqualTo(advancePaymentCreditToPrisoner.amount)
    }

    @Test
    fun `should calculate the advance balance when the advance has some repayments`() {
      val advancePaymentDebitToPrisonerOne = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 1,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      val advancePaymentDebitToPrisonerTwo = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 1,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner, advancePaymentDebitToPrisonerOne, advancePaymentDebitToPrisonerTwo))

      val accountBalance = advanceRecordService.calculateAdvanceBalance(advanceEntity)

      assertThat(accountBalance).isEqualTo(98)
    }

    @Test
    fun `should calculate the advance balance when the advance is paid off`() {
      val advancePaymentDebitToPrisonerOne = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 50,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      val advancePaymentDebitToPrisonerTwo = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 50,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner, advancePaymentDebitToPrisonerOne, advancePaymentDebitToPrisonerTwo))

      val accountBalance = advanceRecordService.calculateAdvanceBalance(advanceEntity)

      assertThat(accountBalance).isEqualTo(0)
    }

    @Test
    fun `should calculate the advance balance where there are write-offs`() {
      val writeOffOne = AdvanceWriteOffEntity(
        advanceRecordId = advanceEntity.id,
        amount = 20,
        createdBy = "TEST",
        createdAt = Instant.now(),
      )

      val writeOffTwo = AdvanceWriteOffEntity(
        advanceRecordId = advanceEntity.id,
        amount = 20,
        createdBy = "TEST",
        createdAt = Instant.now(),
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner))

      whenever(
        advanceWriteOffRepository.findByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(writeOffOne, writeOffTwo))

      val accountBalance = advanceRecordService.calculateAdvanceBalance(advanceEntity)

      assertThat(accountBalance).isEqualTo(60)
    }

    @Test
    fun `should calculate the advance balance where there are write-offs and repayments`() {
      val advancePaymentDebitToPrisoner = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 5,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      val writeOffOne = AdvanceWriteOffEntity(
        advanceRecordId = advanceEntity.id,
        amount = 20,
        createdBy = "TEST",
        createdAt = Instant.now(),
      )

      val writeOffTwo = AdvanceWriteOffEntity(
        advanceRecordId = advanceEntity.id,
        amount = 20,
        createdBy = "TEST",
        createdAt = Instant.now(),
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner, advancePaymentDebitToPrisoner))

      whenever(
        advanceWriteOffRepository.findByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(writeOffOne, writeOffTwo))

      val accountBalance = advanceRecordService.calculateAdvanceBalance(advanceEntity)

      assertThat(accountBalance).isEqualTo(55)
    }
  }

  @Nested
  inner class GetAdvanceBalance {

    val advanceEntity = AdvanceRecordEntity(
      legacyPaymentProfileId = 123,
      legacyInformationNumber = "123312",
      prisonNumber = "A123XS",
      prisonID = "LEI",
      amount = 100,
      createdOn = Instant.now(),
      repaymentStartDate = Instant.now(),
      repaymentAmount = 10,
      reference = "",
      comment = "",
      createdBy = "TEST",
      status = AdvanceStatus.ACTIVE,
      updatedAt = null,
    )

    val advancePaymentCreditToPrisoner = AdvancePaymentEntity(
      advanceRecordId = advanceEntity.id,
      transactionId = UUID.randomUUID(),
      prisonerPostingType = PostingType.CR,
      amount = advanceEntity.amount,
      timestamp = Instant.now(),
      createdBy = "TEST",
    )

    @Test
    fun `Should throw 404 exception when advance entity does not exist`() {
      val error = assertThrows<CustomException> {
        advanceRecordService.getAdvanceBalance(UUID.randomUUID())
      }

      assertThat(error.status).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `Should calculate the balance when an advance is fully repaid`() {
      val advancePaymentDebitToPrisonerOne = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 50,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      val advancePaymentDebitToPrisonerTwo = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 50,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner, advancePaymentDebitToPrisonerOne, advancePaymentDebitToPrisonerTwo))

      whenever(
        advanceRecordRepository.getAdvanceRecordEntityById(advanceEntity.id),
      ).thenReturn(advanceEntity)

      val balance = advanceRecordService.getAdvanceBalance(advanceEntity.id)

      assertThat(balance.balanceOutstanding).isEqualTo(0)
      assertThat(balance.advanceAmount).isEqualTo(advanceEntity.amount)
      assertThat(balance.weeklyPaymentAmount).isEqualTo(advanceEntity.repaymentAmount)
      assertThat(balance.remainingPayments).isEqualTo(0)
    }

    @Test
    fun `Should calculate the balance correctly when there is a balance outstanding`() {
      val advancePaymentDebitToPrisonerOne = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 50,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      val advancePaymentDebitToPrisonerTwo = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 10,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner, advancePaymentDebitToPrisonerOne, advancePaymentDebitToPrisonerTwo))

      whenever(
        advanceRecordRepository.getAdvanceRecordEntityById(advanceEntity.id),
      ).thenReturn(advanceEntity)

      val balance = advanceRecordService.getAdvanceBalance(advanceEntity.id)

      assertThat(balance.balanceOutstanding).isEqualTo(40)
      assertThat(balance.advanceAmount).isEqualTo(advanceEntity.amount)
      assertThat(balance.weeklyPaymentAmount).isEqualTo(advanceEntity.repaymentAmount)
      assertThat(balance.remainingPayments).isEqualTo(4)
    }

    @Test
    fun `Should roundup remaining payments if one is below the weekly repayment amount`() {
      val advancePaymentDebitToPrisonerOne = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 50,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      val advancePaymentDebitToPrisonerTwo = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 31,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner, advancePaymentDebitToPrisonerOne, advancePaymentDebitToPrisonerTwo))

      whenever(
        advanceRecordRepository.getAdvanceRecordEntityById(advanceEntity.id),
      ).thenReturn(advanceEntity)

      val balance = advanceRecordService.getAdvanceBalance(advanceEntity.id)

      assertThat(balance.balanceOutstanding).isEqualTo(19)
      assertThat(balance.advanceAmount).isEqualTo(advanceEntity.amount)
      assertThat(balance.weeklyPaymentAmount).isEqualTo(advanceEntity.repaymentAmount)
      assertThat(balance.remainingPayments).isEqualTo(2)
    }

    @ParameterizedTest
    @CsvSource("REPAID", "WRITTEN_OFF")
    fun `should return a zero balance if advance is not active`(status: AdvanceStatus) {
      val inactiveAdvance = AdvanceRecordEntity(
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "123",
        prisonNumber = "A123BX",
        prisonID = "LEI",
        amount = 10,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 1,
        reference = "",
        comment = "",
        createdBy = "TEST",
        updatedAt = Instant.now(),
        status = status,
      )

      whenever(
        advanceRecordRepository.getAdvanceRecordEntityById(advanceEntity.id),
      ).thenReturn(inactiveAdvance)

      val balance = advanceRecordService.getAdvanceBalance(advanceEntity.id)

      assertThat(balance.balanceOutstanding).isEqualTo(0)
      assertThat(balance.advanceAmount).isEqualTo(inactiveAdvance.amount)
      assertThat(balance.weeklyPaymentAmount).isEqualTo(inactiveAdvance.repaymentAmount)
      assertThat(balance.remainingPayments).isEqualTo(0)
    }
  }

  @Nested
  inner class WriteOffAdvanceFully {

    val advanceWriteOffRequest = AdvanceWriteOffRequest(
      writeOffDateTime = Instant.now(),
      writtenOffBy = "TEST",
      writtenOffAmount = advanceEntity.amount,
    )

    val advanceId = UUID.randomUUID()

    var writeOffAdvanceRecordResponse: AdvanceRecordResponse? = null

    var writeOffEntityCaptor: KArgumentCaptor<AdvanceWriteOffEntity> = argumentCaptor<AdvanceWriteOffEntity>()

    @BeforeEach
    fun setup() {
      whenever { advanceRecordRepository.getAdvanceRecordEntityById(advanceId) }.thenReturn(
        advanceEntity,
      )

      whenever(advanceRecordRepository.save(any<AdvanceRecordEntity>())).thenAnswer { it.arguments[0] }

      whenever(advanceWriteOffRepository.save(writeOffEntityCaptor.capture())).thenAnswer { it.arguments[0] }

      val advancePaymentCreditToPrisoner = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.CR,
        amount = advanceEntity.amount,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner))

      whenever(
        advanceWriteOffRepository.findByAdvanceRecordId(
          eq(advanceEntity.id),
        ),
      ).thenReturn(emptyList())

      writeOffAdvanceRecordResponse = advanceRecordService.writeOffAdvance(advanceId, advanceWriteOffRequest)
    }

    @Test
    fun `Should write-off advance`() {
      assertThat(writeOffAdvanceRecordResponse?.status).isEqualTo(AdvanceStatus.WRITTEN_OFF)
    }

    @Test
    fun `Should store writeOff entity `() {
      verify(advanceWriteOffRepository, times(1)).save(any<AdvanceWriteOffEntity>())

      val writeOffEntity = writeOffEntityCaptor.firstValue
      assertThat(writeOffEntity.amount).isEqualTo(advanceWriteOffRequest.writtenOffAmount)
      assertThat(writeOffEntity.createdBy).isEqualTo(advanceWriteOffRequest.writtenOffBy)
      assertThat(writeOffEntity.createdAt).isEqualTo(advanceWriteOffRequest.writeOffDateTime)
    }
  }

  @Nested
  inner class WriteOffAdvanceWithRepayments {
    val advanceId: UUID = UUID.randomUUID()

    var writeOffResponse: AdvanceRecordResponse? = null

    var writeOffEntityCaptor: KArgumentCaptor<AdvanceWriteOffEntity> = argumentCaptor<AdvanceWriteOffEntity>()

    val advanceRecordCaptor = argumentCaptor<AdvanceRecordEntity>()

    @BeforeEach
    fun setup() {
      whenever { advanceRecordRepository.getAdvanceRecordEntityById(advanceId) }.thenReturn(
        advanceEntity,
      )

      lenient().whenever(advanceRecordRepository.save(advanceRecordCaptor.capture())).thenAnswer { it.arguments[0] }

      whenever(advanceWriteOffRepository.save(writeOffEntityCaptor.capture())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `Should fully write off an advance that has some repayments`() {
      val advancePaymentCreditToPrisoner = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.CR,
        amount = advanceEntity.amount,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      val advancePaymentDebitToPrisonerOne = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 2000,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(advanceWriteOffRepository.findByAdvanceRecordId(advanceEntity.id)).thenReturn(emptyList())

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner, advancePaymentDebitToPrisonerOne))

      val advanceWriteOffRequest = AdvanceWriteOffRequest(
        writeOffDateTime = Instant.now(),
        writtenOffBy = "TEST",
        writtenOffAmount = 8000,
      )

      writeOffResponse = advanceRecordService.writeOffAdvance(advanceId, advanceWriteOffRequest)

      val writeOffEntity = writeOffEntityCaptor.firstValue
      assertThat(writeOffEntity.amount).isEqualTo(advanceWriteOffRequest.writtenOffAmount)

      val updatedEntity = advanceRecordCaptor.firstValue
      assertThat(updatedEntity.status).isEqualTo(AdvanceStatus.WRITTEN_OFF)

      verify(advanceRecordRepository, times(1)).save(any())
    }

    @Test
    fun `Should partially write off an advance`() {
      whenever(advanceWriteOffRepository.findByAdvanceRecordId(advanceEntity.id)).thenReturn(emptyList())

      val advancePaymentCreditToPrisoner = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.CR,
        amount = advanceEntity.amount,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner))

      val advanceWriteOffRequest = AdvanceWriteOffRequest(
        writeOffDateTime = Instant.now(),
        writtenOffBy = "TEST",
        writtenOffAmount = 2000,
      )

      writeOffResponse = advanceRecordService.writeOffAdvance(advanceId, advanceWriteOffRequest)

      val writeOffEntity = writeOffEntityCaptor.firstValue
      assertThat(writeOffEntity.amount).isEqualTo(advanceWriteOffRequest.writtenOffAmount)

      verify(advanceRecordRepository, never()).save(any())
    }
  }

  @Nested
  inner class WriteOffThrowsErrorIfInactiveAdvance {
    val advanceWriteOffRequest = AdvanceWriteOffRequest(
      writeOffDateTime = Instant.now(),
      writtenOffBy = "TEST",
      writtenOffAmount = 10L,
    )

    @ParameterizedTest(name = "{0}")
    @CsvSource("REPAID", "WRITTEN_OFF")
    fun `Should throw a bad request if the advance is`(advanceStatus: AdvanceStatus) {
      val differentAdvanceStatus = advanceEntity.copy(status = advanceStatus)

      whenever { advanceRecordRepository.getAdvanceRecordEntityById(advanceId) }.thenReturn(
        differentAdvanceStatus,
      )

      val errorResponse = assertThrows<CustomException> {
        advanceRecordService.writeOffAdvance(advanceId, advanceWriteOffRequest)
      }

      assertThat(errorResponse.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }
  }

  @Nested
  inner class WriteOffThrowsErrorIfAmountWrittenOffIsIncorrect {
    val advanceWriteOffRequest = AdvanceWriteOffRequest(
      writeOffDateTime = Instant.now(),
      writtenOffBy = "TEST",
      writtenOffAmount = 10L,
    )

    @Test
    fun `Should throw a bad request if the amount written off is greater then the advance balance`() {
      val advancePaymentCreditToPrisoner = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.CR,
        amount = advanceEntity.amount,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner))

      val overpaidWriteOffRequest = advanceWriteOffRequest.copy(writtenOffAmount = 10001L)

      whenever { advanceRecordRepository.getAdvanceRecordEntityById(advanceId) }.thenReturn(
        advanceEntity,
      )

      val errorResponse = assertThrows<CustomException> {
        advanceRecordService.writeOffAdvance(advanceId, overpaidWriteOffRequest)
      }

      assertThat(errorResponse.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Should throw a bad request if the amount written off is greater then the outstanding balance`() {
      val advancePaymentCreditToPrisoner = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.CR,
        amount = advanceEntity.amount,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      val advancePaymentDebitToPrisonerOne = AdvancePaymentEntity(
        advanceRecordId = advanceEntity.id,
        transactionId = UUID.randomUUID(),
        prisonerPostingType = PostingType.DR,
        amount = 8000L,
        timestamp = Instant.now(),
        createdBy = "TEST",
      )

      whenever(
        advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advanceEntity.id),
      ).thenReturn(listOf(advancePaymentCreditToPrisoner, advancePaymentDebitToPrisonerOne))

      val overpaidWriteOffRequest = advanceWriteOffRequest.copy(writtenOffAmount = 3000L)

      whenever { advanceRecordRepository.getAdvanceRecordEntityById(advanceId) }.thenReturn(
        advanceEntity,
      )

      val errorResponse = assertThrows<CustomException> {
        advanceRecordService.writeOffAdvance(advanceId, overpaidWriteOffRequest)
      }

      assertThat(errorResponse.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }
  }
}
