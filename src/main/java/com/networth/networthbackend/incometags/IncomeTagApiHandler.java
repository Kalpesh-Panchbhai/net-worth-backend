package com.networth.networthbackend.incometags;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class IncomeTagApiHandler extends EntityHandlerBase {

    private final IncomeTagRepository repository = new IncomeTagRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        switch (action.toLowerCase()) {
            case Constants.Actions.GET:
                return listByUserId(request);
            default:
                return badRequest(
                        Constants.ErrorCodes.ACTION_NOT_SUPPORTED,
                        String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.INCOME_TAGS, action)
                );
        }
    }

    private APIGatewayProxyResponseEvent listByUserId(APIGatewayProxyRequestEvent request) throws Exception {
        String idStr = pathParam(request, Constants.Path.ID);
        if (idStr == null || idStr.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        long userId;
        try {
            userId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        log.debug(Constants.Logs.INCOME_TAGS_LIST, userId);
        List<IncomeTag> tags = repository.listByUserId(userId);
        log.info(Constants.Logs.INCOME_TAGS_LIST_COUNT, tags.size(), userId);
        return ok(tags);
    }
}
