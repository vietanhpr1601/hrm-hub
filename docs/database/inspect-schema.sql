-- Read-only catalog inspection. No business rows or credentials are exported.
SELECT jsonb_pretty(jsonb_build_object(
  'database', current_database(),
  'server_version', current_setting('server_version'),
  'captured_at', current_timestamp,
  'tables', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
      'schema', ns.nspname,
      'name', tbl.relname,
      'comment', obj_description(tbl.oid, 'pg_class'),
      'columns', COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'position', col.attnum,
        'name', col.attname,
        'type', format_type(col.atttypid, col.atttypmod),
        'nullable', NOT col.attnotnull,
        'default', pg_get_expr(def.adbin, def.adrelid),
        'identity', col.attidentity,
        'generated', col.attgenerated,
        'comment', col_description(tbl.oid, col.attnum)
      ) ORDER BY col.attnum)
        FROM pg_attribute col
        LEFT JOIN pg_attrdef def ON def.adrelid = col.attrelid AND def.adnum = col.attnum
        WHERE col.attrelid = tbl.oid AND col.attnum > 0 AND NOT col.attisdropped), '[]'::jsonb),
      'constraints', COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'name', con.conname,
        'type', con.contype,
        'definition', pg_get_constraintdef(con.oid, true),
        'columns', COALESCE((SELECT jsonb_agg(a.attname ORDER BY k.ord)
          FROM unnest(con.conkey) WITH ORDINALITY k(attnum, ord)
          JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum), '[]'::jsonb),
        'referenced_schema', (SELECT nspname FROM pg_namespace WHERE oid = ref.relnamespace),
        'referenced_table', ref.relname,
        'referenced_columns', COALESCE((SELECT jsonb_agg(a.attname ORDER BY k.ord)
          FROM unnest(con.confkey) WITH ORDINALITY k(attnum, ord)
          JOIN pg_attribute a ON a.attrelid = con.confrelid AND a.attnum = k.attnum), '[]'::jsonb),
        'on_delete', CASE con.confdeltype WHEN 'a' THEN 'NO ACTION' WHEN 'r' THEN 'RESTRICT' WHEN 'c' THEN 'CASCADE' WHEN 'n' THEN 'SET NULL' WHEN 'd' THEN 'SET DEFAULT' END,
        'on_update', CASE con.confupdtype WHEN 'a' THEN 'NO ACTION' WHEN 'r' THEN 'RESTRICT' WHEN 'c' THEN 'CASCADE' WHEN 'n' THEN 'SET NULL' WHEN 'd' THEN 'SET DEFAULT' END,
        'validated', con.convalidated,
        'deferrable', con.condeferrable
      ) ORDER BY con.contype, con.conname)
        FROM pg_constraint con LEFT JOIN pg_class ref ON ref.oid = con.confrelid
        WHERE con.conrelid = tbl.oid AND con.contype IN ('p','u','f','c','x')), '[]'::jsonb),
      'indexes', COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'name', idx.relname,
        'definition', pg_get_indexdef(i.indexrelid),
        'primary', i.indisprimary,
        'unique', i.indisunique,
        'valid', i.indisvalid
      ) ORDER BY idx.relname)
        FROM pg_index i JOIN pg_class idx ON idx.oid = i.indexrelid
        WHERE i.indrelid = tbl.oid), '[]'::jsonb)
    ) ORDER BY ns.nspname, tbl.relname)
    FROM pg_class tbl JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
    WHERE tbl.relkind IN ('r','p') AND ns.nspname NOT IN ('pg_catalog','information_schema')
      AND ns.nspname NOT LIKE 'pg_toast%'
  ), '[]'::jsonb)
));
