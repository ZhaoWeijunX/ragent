/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.evaluation.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.mzt.logapi.starter.annotation.LogRecord;
import com.nageoffer.ai.ragent.audit.constant.BizChangeBizType;
import com.nageoffer.ai.ragent.audit.constant.BizChangeOperationType;
import com.nageoffer.ai.ragent.audit.support.BizChangeLogContext;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.rag.dao.entity.IntentNodeDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.IntentNodeMapper;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalCasePageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalCaseUpsertRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalDatasetCreateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalDatasetPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalDatasetUpdateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalDatasetVersionCreateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalCaseVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalDatasetVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalDatasetVersionVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalImportIssueVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalImportResultVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalValidateResultVO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalCaseDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalDatasetDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalDatasetVersionDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalRunDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalCaseMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalDatasetMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalDatasetVersionMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalRunMapper;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalDatasetService;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalCaseImportSupport;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalJsonSupport;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvalDatasetServiceImpl implements EvalDatasetService {

    private static final Pattern VERSION_SEQ_PATTERN = Pattern.compile("^v(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final int CASE_INSERT_BATCH_SIZE = 200;
    private static final Log BATCH_LOG = LogFactory.getLog(EvalDatasetServiceImpl.class);

    private final EvalDatasetMapper datasetMapper;
    private final EvalDatasetVersionMapper versionMapper;
    private final EvalCaseMapper caseMapper;
    private final EvalRunMapper runMapper;
    private final IntentNodeMapper intentNodeMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final BizChangeLogContext bizChangeLogContext;

    @Override
    public IPage<EvalDatasetVO> pageDatasets(EvalDatasetPageRequest request) {
        Page<EvalDatasetDO> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<EvalDatasetDO> result = datasetMapper.selectPage(page, Wrappers.lambdaQuery(EvalDatasetDO.class)
                .like(StrUtil.isNotBlank(request.getKeyword()), EvalDatasetDO::getName, request.getKeyword())
                .eq(StrUtil.isNotBlank(request.getStatus()), EvalDatasetDO::getStatus, request.getStatus())
                .ne(StrUtil.isBlank(request.getStatus()), EvalDatasetDO::getStatus, EvalWorkbenchConstants.DATASET_ARCHIVED)
                .orderByDesc(EvalDatasetDO::getUpdateTime));
        List<String> datasetIds = result.getRecords().stream().map(EvalDatasetDO::getId).toList();
        if (datasetIds.isEmpty()) {
            return result.convert(dataset -> toDatasetVO(dataset, null));
        }
        Map<String, EvalDatasetVersionDO> latestVersions = new HashMap<>();
        versionMapper.selectList(Wrappers.lambdaQuery(EvalDatasetVersionDO.class)
                        .in(EvalDatasetVersionDO::getDatasetId, datasetIds)
                        .orderByDesc(EvalDatasetVersionDO::getCreateTime))
                .forEach(version -> latestVersions.putIfAbsent(version.getDatasetId(), version));
        return result.convert(dataset -> toDatasetVO(dataset, latestVersions.get(dataset.getId())));
    }

    @Override
    public EvalDatasetVO getDataset(String datasetId) {
        return toDatasetVO(requireDataset(datasetId));
    }

    @Override
    @LogRecord(
            success = "创建评估集：{{#request.name}}",
            fail = "创建评估集失败：{{#_errorMsg}}",
            type = BizChangeBizType.EVAL_DATASET,
            subType = BizChangeOperationType.CREATE,
            bizNo = BizChangeLogContext.BIZ_ID_EXPRESSION,
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    @Transactional(rollbackFor = Exception.class)
    public String createDataset(EvalDatasetCreateRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        String name = StrUtil.trimToNull(request.getName());
        Assert.notBlank(name, () -> new ClientException("评估集名称不能为空"));

        EvalDatasetDO dataset = EvalDatasetDO.builder()
                .name(name)
                .description(StrUtil.trim(request.getDescription()))
                .domain(StrUtil.trim(request.getDomain()))
                .status(EvalWorkbenchConstants.DATASET_ACTIVE)
                .createdBy(UserContext.getUsername())
                .build();
        datasetMapper.insert(dataset);

        EvalDatasetVersionDO version = EvalDatasetVersionDO.builder()
                .datasetId(dataset.getId())
                .version("v1")
                .status(EvalWorkbenchConstants.VERSION_DRAFT)
                .sampleCount(0)
                .build();
        versionMapper.insert(version);

        bizChangeLogContext.put(dataset.getId(), null, dataset);
        return dataset.getId();
    }

    @Override
    @LogRecord(
            success = "更新评估集：{{#datasetId}}",
            fail = "更新评估集失败：{{#_errorMsg}}",
            type = BizChangeBizType.EVAL_DATASET,
            subType = BizChangeOperationType.UPDATE,
            bizNo = "{{#datasetId}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public void updateDataset(String datasetId, EvalDatasetUpdateRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        EvalDatasetDO dataset = requireDataset(datasetId);
        EvalDatasetDO before = BeanUtil.copyProperties(dataset, EvalDatasetDO.class);
        if (request.getName() != null) {
            Assert.notBlank(StrUtil.trim(request.getName()), () -> new ClientException("评估集名称不能为空"));
            dataset.setName(StrUtil.trim(request.getName()));
        }
        if (request.getDescription() != null) {
            dataset.setDescription(StrUtil.trim(request.getDescription()));
        }
        if (request.getDomain() != null) {
            dataset.setDomain(StrUtil.trim(request.getDomain()));
        }
        if (request.getStatus() != null) {
            String status = StrUtil.trim(request.getStatus());
            Assert.isTrue(EvalWorkbenchConstants.DATASET_ACTIVE.equals(status)
                            || EvalWorkbenchConstants.DATASET_ARCHIVED.equals(status),
                    () -> new ClientException("数据集状态仅支持 ACTIVE/ARCHIVED"));
            dataset.setStatus(status);
        }
        datasetMapper.updateById(dataset);
        bizChangeLogContext.put(datasetId, before, datasetMapper.selectById(datasetId));
    }

    @Override
    @LogRecord(
            success = "删除评估集：{{#datasetId}}",
            fail = "删除评估集失败：{{#_errorMsg}}",
            type = BizChangeBizType.EVAL_DATASET,
            subType = BizChangeOperationType.DELETE,
            bizNo = "{{#datasetId}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(String datasetId) {
        EvalDatasetDO dataset = requireDataset(datasetId);
        List<EvalDatasetVersionDO> versions = versionMapper.selectList(Wrappers.lambdaQuery(EvalDatasetVersionDO.class)
                .eq(EvalDatasetVersionDO::getDatasetId, datasetId));
        for (EvalDatasetVersionDO version : versions) {
            Long runCount = runMapper.selectCount(Wrappers.lambdaQuery(EvalRunDO.class)
                    .eq(EvalRunDO::getDatasetVersionId, version.getId()));
            Assert.isTrue(runCount == null || runCount == 0,
                    () -> new ClientException("评估集版本已被 Run 引用，不能删除: " + version.getVersion()));
        }
        EvalDatasetDO before = BeanUtil.copyProperties(dataset, EvalDatasetDO.class);
        for (EvalDatasetVersionDO version : versions) {
            caseMapper.delete(Wrappers.lambdaQuery(EvalCaseDO.class)
                    .eq(EvalCaseDO::getDatasetVersionId, version.getId()));
            versionMapper.deleteById(version.getId());
        }
        datasetMapper.deleteById(datasetId);
        bizChangeLogContext.put(datasetId, before, null);
    }

    @Override
    public List<EvalDatasetVersionVO> listVersions(String datasetId) {
        requireDataset(datasetId);
        return versionMapper.selectList(Wrappers.lambdaQuery(EvalDatasetVersionDO.class)
                        .eq(EvalDatasetVersionDO::getDatasetId, datasetId)
                        .orderByDesc(EvalDatasetVersionDO::getCreateTime))
                .stream()
                .map(v -> toVersionVO(v, requireDataset(datasetId).getName()))
                .toList();
    }

    @Override
    public EvalDatasetVersionVO getVersion(String versionId) {
        EvalDatasetVersionDO version = requireVersion(versionId);
        return toVersionVO(version, requireDataset(version.getDatasetId()).getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDraftVersion(String datasetId, EvalDatasetVersionCreateRequest request) {
        requireDataset(datasetId);
        String versionName = resolveNextVersionName(datasetId, request == null ? null : request.getVersion());
        EvalDatasetVersionDO version = EvalDatasetVersionDO.builder()
                .datasetId(datasetId)
                .version(versionName)
                .status(EvalWorkbenchConstants.VERSION_DRAFT)
                .sampleCount(0)
                .build();
        versionMapper.insert(version);
        return version.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String copyVersion(String versionId) {
        EvalDatasetVersionDO source = requireVersion(versionId);
        String newName = resolveNextVersionName(source.getDatasetId(), null);
        EvalDatasetVersionDO target = EvalDatasetVersionDO.builder()
                .datasetId(source.getDatasetId())
                .version(newName)
                .status(EvalWorkbenchConstants.VERSION_DRAFT)
                .sampleCount(0)
                .build();
        versionMapper.insert(target);

        List<EvalCaseDO> copies = listCases(source.getId()).stream()
                .map(sourceCase -> {
                    EvalCaseDO copy = BeanUtil.copyProperties(sourceCase, EvalCaseDO.class);
                    copy.setId(null);
                    copy.setDatasetVersionId(target.getId());
                    copy.setCreateTime(null);
                    copy.setUpdateTime(null);
                    return copy;
                })
                .toList();
        insertCases(copies);
        refreshVersionStats(target.getId());
        return target.getId();
    }

    @Override
    public void archiveVersion(String versionId) {
        EvalDatasetVersionDO version = requireVersion(versionId);
        Assert.isTrue(EvalWorkbenchConstants.VERSION_PUBLISHED.equals(version.getStatus()),
                () -> new ClientException("仅已发布版本可归档；草稿请直接删除"));
        version.setStatus(EvalWorkbenchConstants.VERSION_ARCHIVED);
        versionMapper.updateById(version);
    }

    @Override
    public void unarchiveVersion(String versionId) {
        EvalDatasetVersionDO version = requireVersion(versionId);
        Assert.isTrue(EvalWorkbenchConstants.VERSION_ARCHIVED.equals(version.getStatus()),
                () -> new ClientException("仅已归档版本可恢复为已发布"));
        version.setStatus(EvalWorkbenchConstants.VERSION_PUBLISHED);
        versionMapper.updateById(version);
    }

    @Override
    @LogRecord(
            success = "删除评估集版本：{{#versionId}}",
            fail = "删除评估集版本失败：{{#_errorMsg}}",
            type = BizChangeBizType.EVAL_DATASET_VERSION,
            subType = BizChangeOperationType.DELETE,
            bizNo = "{{#versionId}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    @Transactional(rollbackFor = Exception.class)
    public void deleteVersion(String versionId) {
        EvalDatasetVersionDO version = requireVersion(versionId);
        Long runCount = runMapper.selectCount(Wrappers.lambdaQuery(EvalRunDO.class)
                .eq(EvalRunDO::getDatasetVersionId, versionId));
        Assert.isTrue(runCount == null || runCount == 0,
                () -> new ClientException("版本已被 Run 引用，不能删除: " + version.getVersion()));
        Long remain = versionMapper.selectCount(Wrappers.lambdaQuery(EvalDatasetVersionDO.class)
                .eq(EvalDatasetVersionDO::getDatasetId, version.getDatasetId()));
        Assert.isTrue(remain == null || remain > 1,
                () -> new ClientException("评估集至少保留一个版本，不能删除最后一个版本"));
        EvalDatasetVersionDO before = BeanUtil.copyProperties(version, EvalDatasetVersionDO.class);
        caseMapper.delete(Wrappers.lambdaQuery(EvalCaseDO.class)
                .eq(EvalCaseDO::getDatasetVersionId, versionId));
        versionMapper.deleteById(versionId);
        bizChangeLogContext.put(versionId, before, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalImportResultVO importCases(String versionId, MultipartFile file) {
        EvalDatasetVersionDO version = requireDraftVersion(versionId);
        Assert.notNull(file, () -> new ClientException("导入文件不能为空"));
        Assert.isFalse(file.isEmpty(), () -> new ClientException("导入文件不能a为空"));

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ClientException("读取导入文件失败: " + ex.getMessage());
        }

        EvalCaseImportSupport.ParseFileResult parsed = EvalCaseImportSupport.parseFile(content);
        Set<String> intentCodes = loadIntentCodes();
        Set<String> bizDocIds = loadBizDocIds();
        Set<String> queryIds = EvalCaseImportSupport.newQueryIdSet();

        List<EvalImportIssueVO> issues = new ArrayList<>(parsed.fileIssues());
        List<EvalCaseUpsertRequest> accepted = new ArrayList<>();
        for (EvalCaseImportSupport.ParsedCase item : parsed.cases()) {
            List<EvalImportIssueVO> caseIssues = new ArrayList<>(item.issues());
            caseIssues.addAll(EvalCaseImportSupport.validateRequest(
                    item.request(), null, queryIds, intentCodes, bizDocIds, false));
            boolean hasError = caseIssues.stream().anyMatch(i -> EvalCaseImportSupport.LEVEL_ERROR.equals(i.getLevel()));
            issues.addAll(caseIssues);
            if (!hasError) {
                accepted.add(item.request());
            }
        }

        int failed = (int) issues.stream().filter(i -> EvalCaseImportSupport.LEVEL_ERROR.equals(i.getLevel())).count();
        int warnings = (int) issues.stream().filter(i -> EvalCaseImportSupport.LEVEL_WARNING.equals(i.getLevel())).count();
        if (!parsed.fileIssues().isEmpty() && accepted.isEmpty()) {
            return EvalImportResultVO.builder()
                    .versionId(versionId)
                    .successCount(0)
                    .failedCount(Math.max(failed, 1))
                    .warningCount(warnings)
                    .issues(issues)
                    .build();
        }

        // 按 queryId upsert：先清再插简化一致性（草稿可重导）
        caseMapper.delete(Wrappers.lambdaQuery(EvalCaseDO.class).eq(EvalCaseDO::getDatasetVersionId, versionId));
        insertCases(accepted.stream().map(req -> EvalCaseImportSupport.toNewCaseDO(versionId, req)).toList());
        refreshVersionStats(versionId);
        return EvalImportResultVO.builder()
                .versionId(versionId)
                .successCount(accepted.size())
                .failedCount(failed)
                .warningCount(warnings)
                .issues(issues)
                .build();
    }

    @Override
    public EvalValidateResultVO validateVersion(String versionId) {
        requireVersion(versionId);
        List<EvalCaseDO> cases = listCases(versionId);
        Set<String> intentCodes = loadIntentCodes();
        Set<String> bizDocIds = loadBizDocIds();
        Set<String> queryIds = EvalCaseImportSupport.newQueryIdSet();
        List<EvalImportIssueVO> issues = new ArrayList<>();
        for (EvalCaseDO c : cases) {
            EvalCaseUpsertRequest req = toUpsertRequest(c);
            issues.addAll(EvalCaseImportSupport.validateRequest(req, null, queryIds, intentCodes, bizDocIds, true));
        }
        int errors = (int) issues.stream().filter(i -> EvalCaseImportSupport.LEVEL_ERROR.equals(i.getLevel())).count();
        int warnings = (int) issues.stream().filter(i -> EvalCaseImportSupport.LEVEL_WARNING.equals(i.getLevel())).count();
        return EvalValidateResultVO.builder()
                .versionId(versionId)
                .publishable(errors == 0 && !cases.isEmpty())
                .sampleCount(cases.size())
                .errorCount(errors)
                .warningCount(warnings)
                .issues(issues)
                .build();
    }

    @Override
    @LogRecord(
            success = "发布评估集版本：{{#versionId}}",
            fail = "发布评估集版本失败：{{#_errorMsg}}",
            type = BizChangeBizType.EVAL_DATASET_VERSION,
            subType = BizChangeOperationType.UPDATE,
            bizNo = "{{#versionId}}",
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    @Transactional(rollbackFor = Exception.class)
    public void publishVersion(String versionId) {
        EvalDatasetVersionDO version = requireDraftVersion(versionId);
        EvalDatasetVersionDO before = BeanUtil.copyProperties(version, EvalDatasetVersionDO.class);
        EvalValidateResultVO validate = validateVersion(versionId);
        Assert.isTrue(validate.isPublishable(), () -> new ClientException(
                "版本不可发布：错误 " + validate.getErrorCount() + "，样本 " + validate.getSampleCount()));

        List<EvalCaseDO> cases = listCases(versionId);
        version.setStatus(EvalWorkbenchConstants.VERSION_PUBLISHED);
        version.setSampleCount(cases.size());
        version.setContentHash(EvalCaseImportSupport.contentHash(cases));
        version.setPublishedBy(UserContext.getUsername());
        version.setPublishedAt(new Date());
        versionMapper.updateById(version);
        bizChangeLogContext.put(versionId, before, versionMapper.selectById(versionId));
    }

    @Override
    public String exportVersionJsonl(String versionId) {
        requireVersion(versionId);
        return EvalCaseImportSupport.toExportJsonl(listCases(versionId));
    }

    @Override
    public IPage<EvalCaseVO> pageCases(String versionId, EvalCasePageRequest request) {
        requireVersion(versionId);
        Page<EvalCaseDO> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<EvalCaseDO> result = caseMapper.selectPage(page, Wrappers.lambdaQuery(EvalCaseDO.class)
                .eq(EvalCaseDO::getDatasetVersionId, versionId)
                .and(StrUtil.isNotBlank(request.getKeyword()), w -> w
                        .like(EvalCaseDO::getQueryId, request.getKeyword())
                        .or()
                        .like(EvalCaseDO::getQuery, request.getKeyword()))
                .eq(StrUtil.isNotBlank(request.getIntentL2()), EvalCaseDO::getIntentL2, request.getIntentL2())
                .eq(StrUtil.isNotBlank(request.getDifficulty()), EvalCaseDO::getDifficulty, request.getDifficulty())
                .eq(request.getRequiresRag() != null, EvalCaseDO::getRequiresRag, request.getRequiresRag())
                .orderByAsc(EvalCaseDO::getQueryId));
        return result.convert(this::toCaseVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createCase(String versionId, EvalCaseUpsertRequest request) {
        requireDraftVersion(versionId);
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        List<EvalImportIssueVO> issues = EvalCaseImportSupport.validateRequest(
                request, null, EvalCaseImportSupport.newQueryIdSet(), loadIntentCodes(), loadBizDocIds(), false);
        assertNoError(issues);
        Long exists = caseMapper.selectCount(Wrappers.lambdaQuery(EvalCaseDO.class)
                .eq(EvalCaseDO::getDatasetVersionId, versionId)
                .eq(EvalCaseDO::getQueryId, StrUtil.trim(request.getQueryId())));
        Assert.isTrue(exists == null || exists == 0, () -> new ClientException("queryId 已存在"));
        EvalCaseDO record = EvalCaseImportSupport.toNewCaseDO(versionId, request);
        caseMapper.insert(record);
        refreshVersionStats(versionId);
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCase(String caseId, EvalCaseUpsertRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        EvalCaseDO record = requireCase(caseId);
        requireDraftVersion(record.getDatasetVersionId());
        if (request.getQueryId() != null && !Objects.equals(StrUtil.trim(request.getQueryId()), record.getQueryId())) {
            throw new ClientException("不允许修改 queryId，请删除后重建");
        }
        EvalCaseDO temp = BeanUtil.copyProperties(record, EvalCaseDO.class);
        EvalCaseImportSupport.applyUpdate(temp, request);
        EvalCaseUpsertRequest merged = toUpsertRequest(temp);
        List<EvalImportIssueVO> issues = EvalCaseImportSupport.validateRequest(
                merged, null, EvalCaseImportSupport.newQueryIdSet(), loadIntentCodes(), loadBizDocIds(), false);
        assertNoError(issues);
        EvalCaseImportSupport.applyUpdate(record, request);
        caseMapper.updateById(record);
        refreshVersionStats(record.getDatasetVersionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(String caseId) {
        EvalCaseDO record = requireCase(caseId);
        requireDraftVersion(record.getDatasetVersionId());
        caseMapper.deleteById(caseId);
        refreshVersionStats(record.getDatasetVersionId());
    }

    private void assertNoError(List<EvalImportIssueVO> issues) {
        String message = issues.stream()
                .filter(i -> EvalCaseImportSupport.LEVEL_ERROR.equals(i.getLevel()))
                .map(EvalImportIssueVO::getMessage)
                .collect(Collectors.joining("; "));
        Assert.isTrue(StrUtil.isBlank(message), () -> new ClientException(message));
    }

    private void refreshVersionStats(String versionId) {
        requireVersion(versionId);
        List<EvalCaseDO> cases = listCases(versionId);
        versionMapper.update(null, Wrappers.lambdaUpdate(EvalDatasetVersionDO.class)
                .eq(EvalDatasetVersionDO::getId, versionId)
                .set(EvalDatasetVersionDO::getSampleCount, cases.size())
                .set(EvalDatasetVersionDO::getContentHash,
                        cases.isEmpty() ? null : EvalCaseImportSupport.contentHash(cases)));
    }

    private int countCases(String versionId) {
        Long count = caseMapper.selectCount(Wrappers.lambdaQuery(EvalCaseDO.class)
                .eq(EvalCaseDO::getDatasetVersionId, versionId));
        return count == null ? 0 : count.intValue();
    }

    private void insertCases(List<EvalCaseDO> cases) {
        if (cases.isEmpty()) {
            return;
        }
        boolean inserted = SqlHelper.executeBatch(EvalCaseDO.class, BATCH_LOG, cases, CASE_INSERT_BATCH_SIZE,
                (sqlSession, record) -> sqlSession.getMapper(EvalCaseMapper.class).insert(record));
        Assert.isTrue(inserted, () -> new ClientException("批量写入评测样本失败"));
    }

    private String resolveNextVersionName(String datasetId, String preferred) {
        if (StrUtil.isNotBlank(preferred)) {
            String name = StrUtil.trim(preferred);
            Assert.isTrue(!versionNameExists(datasetId, name), () -> new ClientException("版本号已存在: " + name));
            return name;
        }
        List<EvalDatasetVersionDO> versions = versionMapper.selectList(Wrappers.lambdaQuery(EvalDatasetVersionDO.class)
                .eq(EvalDatasetVersionDO::getDatasetId, datasetId)
                .select(EvalDatasetVersionDO::getVersion));
        int maxSeq = 0;
        Set<String> existing = new HashSet<>();
        for (EvalDatasetVersionDO version : versions) {
            String name = StrUtil.trim(version.getVersion());
            if (StrUtil.isBlank(name)) {
                continue;
            }
            existing.add(name);
            Matcher matcher = VERSION_SEQ_PATTERN.matcher(name);
            if (matcher.matches()) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(matcher.group(1)));
            }
        }
        int next = maxSeq + 1;
        String candidate = "v" + next;
        while (existing.contains(candidate)) {
            next++;
            candidate = "v" + next;
        }
        return candidate;
    }

    private boolean versionNameExists(String datasetId, String versionName) {
        Long count = versionMapper.selectCount(Wrappers.lambdaQuery(EvalDatasetVersionDO.class)
                .eq(EvalDatasetVersionDO::getDatasetId, datasetId)
                .eq(EvalDatasetVersionDO::getVersion, versionName));
        return count != null && count > 0;
    }

    private EvalDatasetDO requireDataset(String datasetId) {
        Assert.notBlank(datasetId, () -> new ClientException("datasetId 不能为空"));
        EvalDatasetDO dataset = datasetMapper.selectById(datasetId);
        Assert.notNull(dataset, () -> new ClientException("评估集不存在"));
        return dataset;
    }

    private EvalDatasetVersionDO requireVersion(String versionId) {
        Assert.notBlank(versionId, () -> new ClientException("versionId 不能为空"));
        EvalDatasetVersionDO version = versionMapper.selectById(versionId);
        Assert.notNull(version, () -> new ClientException("评估集版本不存在"));
        return version;
    }

    private EvalDatasetVersionDO requireDraftVersion(String versionId) {
        EvalDatasetVersionDO version = requireVersion(versionId);
        Assert.isTrue(EvalWorkbenchConstants.VERSION_DRAFT.equals(version.getStatus()),
                () -> new ClientException("仅草稿版本可修改；已发布版本请复制为新草稿"));
        return version;
    }

    private EvalCaseDO requireCase(String caseId) {
        Assert.notBlank(caseId, () -> new ClientException("caseId 不能为空"));
        EvalCaseDO record = caseMapper.selectById(caseId);
        Assert.notNull(record, () -> new ClientException("样本不存在"));
        return record;
    }

    private List<EvalCaseDO> listCases(String versionId) {
        return caseMapper.selectList(Wrappers.lambdaQuery(EvalCaseDO.class)
                .eq(EvalCaseDO::getDatasetVersionId, versionId)
                .orderByAsc(EvalCaseDO::getQueryId));
    }

    private Set<String> loadIntentCodes() {
        return intentNodeMapper.selectList(Wrappers.lambdaQuery(IntentNodeDO.class)
                        .select(IntentNodeDO::getIntentCode))
                .stream()
                .map(IntentNodeDO::getIntentCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Set<String> loadBizDocIds() {
        return knowledgeDocumentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .select(KnowledgeDocumentDO::getDocName))
                .stream()
                .map(KnowledgeDocumentDO::getDocName)
                .filter(StrUtil::isNotBlank)
                .map(EvalCaseImportSupport::stripDocExtension)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private EvalDatasetVO toDatasetVO(EvalDatasetDO dataset) {
        EvalDatasetVersionDO latest = versionMapper.selectOne(Wrappers.lambdaQuery(EvalDatasetVersionDO.class)
                .eq(EvalDatasetVersionDO::getDatasetId, dataset.getId())
                .orderByDesc(EvalDatasetVersionDO::getCreateTime)
                .last("limit 1"));
        return toDatasetVO(dataset, latest);
    }

    private EvalDatasetVO toDatasetVO(EvalDatasetDO dataset, EvalDatasetVersionDO latest) {
        return EvalDatasetVO.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .domain(dataset.getDomain())
                .status(dataset.getStatus())
                .createdBy(dataset.getCreatedBy())
                .latestVersion(latest == null ? null : latest.getVersion())
                .latestVersionStatus(latest == null ? null : latest.getStatus())
                .latestSampleCount(latest == null || latest.getSampleCount() == null ? 0 : latest.getSampleCount())
                .createTime(dataset.getCreateTime())
                .updateTime(dataset.getUpdateTime())
                .build();
    }

    private EvalDatasetVersionVO toVersionVO(EvalDatasetVersionDO version, String datasetName) {
        return EvalDatasetVersionVO.builder()
                .id(version.getId())
                .datasetId(version.getDatasetId())
                .datasetName(datasetName)
                .version(version.getVersion())
                .status(version.getStatus())
                .sampleCount(countCases(version.getId()))
                .contentHash(version.getContentHash())
                .publishedBy(version.getPublishedBy())
                .publishedAt(version.getPublishedAt())
                .createTime(version.getCreateTime())
                .updateTime(version.getUpdateTime())
                .build();
    }

    private EvalCaseVO toCaseVO(EvalCaseDO record) {
        return EvalCaseVO.builder()
                .id(record.getId())
                .datasetVersionId(record.getDatasetVersionId())
                .queryId(record.getQueryId())
                .query(record.getQuery())
                .intentL1(record.getIntentL1())
                .intentL2(record.getIntentL2())
                .difficulty(record.getDifficulty())
                .requiresRag(record.getRequiresRag())
                .expectedAnswerType(record.getExpectedAnswerType())
                .expectedDocIds(EvalJsonSupport.toStringList(record.getExpectedDocIds()))
                .niceToHaveDocIds(EvalJsonSupport.toStringList(record.getNiceToHaveDocIds()))
                .groundTruth(record.getGroundTruth())
                .trapType(record.getTrapType())
                .enabledMetrics(EvalJsonSupport.toStringList(record.getEnabledMetrics()))
                .tags(EvalJsonSupport.toStringList(record.getTags()))
                .metadata(EvalJsonSupport.toMap(record.getMetadata()))
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }

    private EvalCaseUpsertRequest toUpsertRequest(EvalCaseDO record) {
        EvalCaseUpsertRequest req = new EvalCaseUpsertRequest();
        req.setQueryId(record.getQueryId());
        req.setQuery(record.getQuery());
        req.setIntentL1(record.getIntentL1());
        req.setIntentL2(record.getIntentL2());
        req.setDifficulty(record.getDifficulty());
        req.setRequiresRag(record.getRequiresRag());
        req.setExpectedAnswerType(record.getExpectedAnswerType());
        req.setExpectedDocIds(EvalJsonSupport.toStringList(record.getExpectedDocIds()));
        req.setNiceToHaveDocIds(EvalJsonSupport.toStringList(record.getNiceToHaveDocIds()));
        req.setGroundTruth(record.getGroundTruth());
        req.setTrapType(record.getTrapType());
        req.setEnabledMetrics(EvalJsonSupport.toStringList(record.getEnabledMetrics()));
        req.setTags(EvalJsonSupport.toStringList(record.getTags()));
        req.setMetadata(EvalJsonSupport.toMap(record.getMetadata()));
        return req;
    }
}
