Lixiangpu:
function: interface <=> web client 
function: interface =>  switch-qosline  create/change/delete(queueId,min_speed,max_speed)
function: interface => switch-openfow create(source ip,source port,queueId) return flowId
                                      change(queueId/outport,flowId)
                                      delete(flowId)
