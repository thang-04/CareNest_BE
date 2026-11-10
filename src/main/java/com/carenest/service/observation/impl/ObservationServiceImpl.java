package com.carenest.service.observation.impl;

import com.carenest.dto.observation.ObservationCriteriaResponse;
import com.carenest.dto.observation.ObservationResponse;
import com.carenest.dto.observation.RecordObservationsRequest;
import com.carenest.dto.observation.RecordObservationsRequest.Entry;
import com.carenest.dto.observation.RecordObservationsResponse;
import com.carenest.entity.observation.CriteriaOption;
import com.carenest.entity.observation.Observation;
import com.carenest.entity.observation.ObservationCriteria;
import com.carenest.entity.observation.ObservationValue;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.observation.ObservationMapper;
import com.carenest.repository.observation.CriteriaOptionRepository;
import com.carenest.repository.observation.ObservationCriteriaRepository;
import com.carenest.repository.observation.ObservationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.observation.ObservationService;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObservationServiceImpl implements ObservationService {
    private static final String LOG_PREFIX = "[ObservationServiceImpl]";
    static final long MAX_QUERY_DAYS = 184;

    private final ObservationRepository observationRepository;
    private final ObservationCriteriaRepository criteriaRepository;
    private final CriteriaOptionRepository optionRepository;
    private final EnrollmentService enrollmentService;
    private final ClassroomAccessPolicy classroomAccessPolicy;
    private final ChildAccessPolicy childAccessPolicy;
    private final ObservationMapper observationMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ObservationCriteriaResponse> listCriteria(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listCriteria|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<ObservationCriteria> criteria = criteriaRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        Map<Long, List<CriteriaOption>> optionsByCriteria = optionRepository
                .findByCriteriaIdInOrderBySortOrderAscIdAsc(
                        criteria.stream().map(ObservationCriteria::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(CriteriaOption::getCriteriaId));
        List<ObservationCriteriaResponse> responses = criteria.stream()
                .map(item -> observationMapper.toCriteriaResponse(
                        item, optionsByCriteria.getOrDefault(item.getId(), List.of())))
                .toList();
        log.info("{}|END|count={}", logPrefix, responses.size());
        return responses;
    }

    @Override
    @Transactional
    public RecordObservationsResponse recordObservations(
            CurrentUser currentUser, Long classroomId, RecordObservationsRequest request) {
        String logPrefix = LOG_PREFIX + "|recordObservations|userId=" + currentUser.userId() + "|classroomId="
                + classroomId + "|date=" + request.observedOn();
        log.info("{}|START", logPrefix);
        try {
            if (request.observedOn().isAfter(LocalDate.now(clock))) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "observedOn must not be in the future");
            }
            classroomAccessPolicy.checkCanViewClassroom(currentUser, classroomId);
            Set<Long> enrolled = new HashSet<>(enrollmentService.findActiveChildIds(classroomId, request.observedOn()));
            Set<Long> seenChildren = new HashSet<>();
            for (Entry entry : request.entries()) {
                if (!enrolled.contains(entry.childId())) {
                    throw new GlobalException(
                            ApiCode.BAD_REQUEST,
                            "Child " + entry.childId() + " is not enrolled in this class on that date");
                }
                if (!seenChildren.add(entry.childId())) {
                    throw new GlobalException(ApiCode.BAD_REQUEST, "Duplicate child " + entry.childId());
                }
            }

            Map<Long, ObservationCriteria> activeCriteria =
                    criteriaRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                            .collect(Collectors.toMap(ObservationCriteria::getId, Function.identity()));
            Map<Long, CriteriaOption> options =
                    optionRepository.findByCriteriaIdInOrderBySortOrderAscIdAsc(activeCriteria.keySet()).stream()
                            .collect(Collectors.toMap(CriteriaOption::getId, Function.identity()));
            Map<Long, Observation> existing =
                    observationRepository.findByChildrenOnDate(seenChildren, request.observedOn()).stream()
                            .collect(Collectors.toMap(Observation::getChildId, Function.identity()));

            List<Observation> toSave = new ArrayList<>();
            for (Entry entry : request.entries()) {
                List<ObservationValue> values = toValues(entry, activeCriteria, options);
                Observation observation = existing.getOrDefault(
                        entry.childId(), Observation.create(entry.childId(), classroomId, request.observedOn()));
                observation.record(normalizeNote(entry.note()), values, currentUser.userId());
                toSave.add(observation);
            }
            observationRepository.saveAll(toSave);
            log.info("{}|END|saved={}", logPrefix, toSave.size());
            return new RecordObservationsResponse(classroomId, request.observedOn(), toSave.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObservationResponse> listChildObservations(
            CurrentUser currentUser, Long childId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|listChildObservations|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > MAX_QUERY_DAYS) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Invalid period (max " + MAX_QUERY_DAYS + " days)");
            }
            childAccessPolicy.checkCanViewChild(currentUser, childId);
            List<Observation> observations = observationRepository.findByChildInPeriod(childId, from, to);
            Map<Long, ObservationCriteria> criteria = criteriaRepository.findAll().stream()
                    .collect(Collectors.toMap(ObservationCriteria::getId, Function.identity()));
            Map<Long, CriteriaOption> options = optionRepository.findAll().stream()
                    .collect(Collectors.toMap(CriteriaOption::getId, Function.identity()));
            List<ObservationResponse> responses = observations.stream()
                    .map(observation -> observationMapper.toObservationResponse(observation, criteria, options))
                    .toList();
            log.info("{}|END|count={}", logPrefix, responses.size());
            return responses;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static List<ObservationValue> toValues(
            Entry entry, Map<Long, ObservationCriteria> activeCriteria, Map<Long, CriteriaOption> options) {
        Set<Long> seenCriteria = new HashSet<>();
        List<ObservationValue> values = new ArrayList<>();
        for (RecordObservationsRequest.Value value : entry.values()) {
            CriteriaOption option = options.get(value.optionId());
            boolean valid = activeCriteria.containsKey(value.criteriaId())
                    && option != null
                    && option.getCriteriaId().equals(value.criteriaId());
            if (!valid) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Invalid criteria/option for child " + entry.childId());
            }
            if (!seenCriteria.add(value.criteriaId())) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Duplicate criteria for child " + entry.childId());
            }
            values.add(ObservationValue.of(value.criteriaId(), value.optionId()));
        }
        return values;
    }

    private static String normalizeNote(String note) {
        return StringUtils.hasText(note) ? note.strip() : null;
    }
}
