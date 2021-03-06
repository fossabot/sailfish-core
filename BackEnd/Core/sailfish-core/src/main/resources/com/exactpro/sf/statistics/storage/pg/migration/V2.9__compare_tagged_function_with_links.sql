DROP FUNCTION tagged_sets_comparison(bigint[], bigint, bigint[], bigint);

CREATE OR REPLACE FUNCTION tagged_sets_comparison(IN first_ids bigint[], IN first_count bigint, IN second_ids bigint[], IN second_count bigint)
  RETURNS TABLE(testcaseid character varying, ftcid bigint, fmatrix character varying, fstatus integer, fdescription character varying, ffailreason character varying, ffailedactions text, fcomment text, fuserstatus text, ftcrid bigint, fmrid bigint, fstarttime timestamp without time zone, ffinishtime timestamp without time zone, ftags text, fhash integer,
  fsfhost character varying, fsfname character varying, fsfport integer,
  fsfcurrenthost character varying, fsfcurrentname character varying, fsfcurrentport integer,
  freportfolder text, freportfile text,
  stcid bigint, smatrix character varying, sstatus integer, sdescription character varying, sfailreason character varying, sfailedactions text, scomment text, suserstatus text, stcrid bigint, smrid bigint, sstarttime timestamp without time zone, sfinishtime timestamp without time zone, stags text, shash integer,
  ssfhost character varying, ssfname character varying, ssfport integer,
  ssfcurrenthost character varying, ssfcurrentname character varying, ssfcurrentport integer,
  sreportfolder text, sreportfile text) AS
$BODY$
BEGIN

RETURN QUERY

WITH first_tcrs AS (

    SELECT TC.id AS tcid, TC.testcaseid, M.name, TCR.status, TCR.description, TCR.failreason, TCR.comment, TS.name AS userStatus, TCR.id AS tcrid, MR.id AS mrid,
        TCR.starttime, TCR.finishtime, TCR.hash,
        (SELECT string_agg(T.name, ', ' ORDER BY T.name)  FROM sttags T JOIN stmrtags MRT ON MRT.tag_id = T.id WHERE MRT.mr_id = MR.id ) AS tags,
        string_agg(AR.rank::text || 'ARFRSPLITTER' || AR.failreason, 'FACTSPLITTER' ORDER BY AR.rank) AS failedactions,
        MR.sf_id as sfid, MR.sf_current_id as sfcurrentid, MR.reportFolder as reportfolder, TCR.reportFile as reportfile
    FROM
        (SELECT TC.id, max(TCR.startTime) AS startTime -- Latest execution of each test case tagged with set 1
        FROM sttestcaseruns TCR
            JOIN stmatrixruns MR ON TCR.matrix_run_id = MR.id
            JOIN sttestcases TC ON TCR.tc_id = TC.id
        WHERE EXISTS (

            SELECT MR2.id
            FROM stmatrixruns MR2
                JOIN stmrtags MRT ON MR2.id = MRT.mr_id
                JOIN sttags T ON MRT.tag_id = T.id
            WHERE MR2.id = MR.id
            AND T.id = ANY (first_ids)         -------- tags1 parameter
            GROUP BY MR2.id
            HAVING count(*) >= first_count -------- tags1 count parameter
        )
        AND TC.testcaseid <> '_unknown_tc_'
        GROUP BY 1 ) AS X

        JOIN sttestcaseruns TCR ON TCR.tc_id = X.id AND TCR.startTime = X.startTime
        JOIN stmatrixruns MR ON TCR.matrix_run_id = MR.id
        JOIN sttestcases TC ON TCR.tc_id = TC.id
        LEFT JOIN sttcrstatus TS ON TCR.status_id = TS.id
        JOIN stmatrices M ON MR.matrix_id = M.id
        LEFT JOIN stactionruns AR ON AR.tc_run_id = TCR.id AND AR.failreason IS NOT NULL
		GROUP BY tcid, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14
),

second_tcrs AS (

    SELECT TC.id AS tcid, TC.testcaseid, M.name, TCR.status, TCR.description, TCR.failreason, TCR.comment, TS.name AS userStatus, TCR.id AS tcrid, MR.id AS mrid,
        TCR.starttime, TCR.finishtime, TCR.hash,
        (SELECT string_agg(T.name, ', ' ORDER BY T.name)  FROM sttags T JOIN stmrtags MRT ON MRT.tag_id = T.id WHERE MRT.mr_id = MR.id ) AS tags,
        string_agg(AR.rank::text || 'ARFRSPLITTER' || AR.failreason, 'FACTSPLITTER' ORDER BY AR.rank) AS failedactions,
        MR.sf_id as sfid, MR.sf_current_id as sfcurrentid, MR.reportFolder as reportfolder, TCR.reportFile as reportfile
    FROM
        (SELECT TC.id, max(TCR.startTime) AS startTime -- Latest execution of each test case tagged with set 2
        FROM sttestcaseruns TCR
            JOIN stmatrixruns MR ON TCR.matrix_run_id = MR.id
            JOIN sttestcases TC ON TCR.tc_id = TC.id
        WHERE EXISTS (

            SELECT MR2.id
            FROM stmatrixruns MR2
                JOIN stmrtags MRT ON MR2.id = MRT.mr_id
                JOIN sttags T ON MRT.tag_id = T.id
            WHERE MR2.id = MR.id
            AND T.id = ANY (second_ids)         -------- tags2 parameter
            GROUP BY MR2.id
            HAVING count(*) >= second_count -------- tags2 count parameter
        )
        AND TC.testcaseid <> '_unknown_tc_'
        GROUP BY 1 ) AS X

        JOIN sttestcaseruns TCR ON TCR.tc_id = X.id AND TCR.startTime = X.startTime
        JOIN stmatrixruns MR ON TCR.matrix_run_id = MR.id
        JOIN sttestcases TC ON TCR.tc_id = TC.id
        LEFT JOIN sttcrstatus TS ON TCR.status_id = TS.id
        JOIN stmatrices M ON MR.matrix_id = M.id
        LEFT JOIN stactionruns AR ON AR.tc_run_id = TCR.id AND AR.failreason IS NOT NULL
		GROUP BY tcid, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14

)

SELECT RESULT.testcaseid,
    RESULT.ftcid, RESULT.fmatrix, RESULT.fstatus, RESULT.fdescription, RESULT.ffailreason, RESULT.ffailedactions, RESULT.fcomment, RESULT.fuserStatus, RESULT.ftcrid, RESULT.fmrid,
        RESULT.fstarttime, RESULT.ffinishtime, RESULT.ftags, RESULT.fhash, FSF.host, FSF.name, FSF.port, CFSF.host, CFSF.name, CFSF.port, RESULT.freportfolder, RESULT.freportfile,
    RESULT.stcid, RESULT.smatrix, RESULT.sstatus, RESULT.sdescription, RESULT.sfailreason, RESULT.sfailedactions, RESULT.scomment, RESULT.suserStatus, RESULT.stcrid, RESULT.smrid,
        RESULT.sstarttime, RESULT.sfinishtime, RESULT.stags, RESULT.shash, SSF.host, SSF.name, SSF.port, CSSF.host, CSSF.name, CSSF.port, RESULT.sreportfolder, RESULT.sreportfile
FROM
		(SELECT coalesce(F.testcaseid, S.testcaseid) AS testcaseid,
	    F.tcid AS ftcid, F.name AS fmatrix, F.status AS fstatus, F.description AS fdescription, F.failreason AS ffailreason, F.failedactions AS ffailedactions, F.comment AS fcomment,
	    F.userStatus AS fuserStatus, F.tcrid AS ftcrid, F.mrid AS fmrid, F.starttime AS fstarttime, F.finishtime AS ffinishtime, F.tags AS ftags, F.hash AS fhash,
	    F.sfid as fsfid, F.sfcurrentid as fsfcurrentid, F.reportfolder as freportfolder, F.reportfile as freportfile,
	    S.tcid AS stcid, S.name AS smatrix, S.status AS sstatus, S.description AS sdescription, S.failreason AS sfailreason, S.failedactions AS sfailedactions, S.comment AS scomment,
	    S.userStatus AS suserStatus, S.tcrid AS stcrid, S.mrid AS smrid, S.starttime AS sstarttime, S.finishtime AS sfinishtime, S.tags AS stags, S.hash AS shash,
	    S.sfid as ssfid, S.sfcurrentid as ssfcurrentid, S.reportfolder as sreportfolder, S.reportfile as sreportfile

		FROM first_tcrs F FULL JOIN second_tcrs S ON F.tcid = S.tcid
		ORDER BY 1) as RESULT
LEFT JOIN stsfinstances FSF ON RESULT.fsfid = FSF.id
LEFT JOIN stsfinstances CFSF ON RESULT.fsfcurrentid = CFSF.id
LEFT JOIN stsfinstances SSF ON RESULT.ssfid = SSF.id
LEFT JOIN stsfinstances CSSF ON RESULT.ssfcurrentid = CSSF.id;

END;
$BODY$
  LANGUAGE plpgsql;