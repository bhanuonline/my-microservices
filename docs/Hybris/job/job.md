SELECT
{j.code}        AS JobCode,
{cj.code}       AS CronJobCode,
{es.code}       AS Status,
{cj.active}      AS TriggerActive,
{t.cronExpression},
{cj.nodeID} 	 As Node,
{cj.startTime} AS LastStartTime,
{cj.endTime}	 As LastEndTime
FROM {Job AS j
JOIN CronJob AS cj ON {cj.job} = {j.pk}
LEFT JOIN Trigger AS t ON {t.cronJob} = {cj.pk}
LEFT JOIN EnumerationValue AS es ON {cj.status} = {es.pk}}
WHERE {j.code} <> 'ImpEx-Import'
ORDER BY
CASE WHEN {cj.active} = 1 THEN 0 ELSE 1 END,
{cj.nodeID};
