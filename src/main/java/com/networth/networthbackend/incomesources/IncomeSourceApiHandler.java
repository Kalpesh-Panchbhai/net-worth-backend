package com.networth.networthbackend.incomesources;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

@Slf4j
public class IncomeSourceApiHandler extends EntityHandlerBase {

    private final IncomeSourceRepository repository = new IncomeSourceRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.CREATE -> create(request);
            case Constants.Actions.GET -> listByUserId(request);
            case Constants.Actions.UPDATE -> setDefault(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.INCOME_SOURCES, action));
        };
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long userId = json.hasNonNull(Constants.Query.USER_ID) ? json.get(Constants.Query.USER_ID).asLong() : null;
        String name = json.hasNonNull(Constants.Query.NAME) ? json.get(Constants.Query.NAME).asText() : null;
        boolean isDefault = json.hasNonNull(Constants.Query.IS_DEFAULT) && json.get(Constants.Query.IS_DEFAULT).asBoolean();
        if (userId == null || userId <= 0) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_SOURCE_USER_ID_REQUIRED);
        }
        if (name == null || name.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_SOURCE_NAME_REQUIRED);
        }
        log.debug(Constants.Logs.INCOME_SOURCE_CREATE, userId, name, isDefault);
        IncomeSource created;
        try {
            created = repository.create(userId, name, isDefault);
        } catch (SQLIntegrityConstraintViolationException e) {
            // Duplicate (userId, name) per unique key uk_income_sources_user_name
            log.warn(Constants.Logs.INCOME_SOURCE_NAME_DUPLICATE, userId, name);
            return ApiGatewayResponses.error(Constants.Http.Status.CONFLICT, Constants.ErrorCodes.CONFLICT, Constants.Messages.INCOME_SOURCE_NAME_DUPLICATE);
        }
        log.info(Constants.Logs.INCOME_SOURCE_CREATED, created.getId(), created.getUserId(), created.getName(), created.isDefault());
        return created(created);
    }

    private APIGatewayProxyResponseEvent listByUserId(APIGatewayProxyRequestEvent request) throws Exception {
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        log.debug(Constants.Logs.INCOME_SOURCES_LIST, userId);
        List<IncomeSource> sources = repository.listByUserId(userId);
        log.info(Constants.Logs.INCOME_SOURCES_LIST_COUNT, sources.size(), userId);
        return ok(sources);
    }

    private APIGatewayProxyResponseEvent setDefault(APIGatewayProxyRequestEvent request) throws Exception {
        Long sourceId = parsePathId(request);
        if (sourceId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        log.debug(Constants.Logs.INCOME_SOURCE_SET_DEFAULT, sourceId);
        boolean ok = repository.setDefault(sourceId);
        if (!ok) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.INCOME_SOURCE_NOT_FOUND);
        }
        return ApiGatewayResponses.noContent();
    }

    private Long parsePathId(APIGatewayProxyRequestEvent request) {
        String idStr = pathParam(request, Constants.Path.ID);
        if (idStr == null || idStr.isBlank()) return null;
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
