import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const read = name => JSON.parse(fs.readFileSync(path.join(here, name), 'utf8'));
const schema = read('schema.json');
const descriptions = { ...read('current-descriptions.json'), ...read('other-descriptions.json') };
const tables = schema.tables;
const byName = new Map(tables.map(t => [t.name, t]));
const common = {
  id: 'Định danh UUID của bản ghi; backend hiện tại sinh qua JPA, không có DEFAULT UUID tại DB.',
  create_date: 'Thời điểm tạo, được BaseModel gán khi lưu mới qua JPA.',
  created_by: 'Tên đăng nhập tạo bản ghi; chuỗi audit, không phải khóa ngoại đến tbl_user.',
  modify_date: 'Thời điểm tạo/cập nhật gần nhất qua JPA.',
  modified_by: 'Tên đăng nhập sửa gần nhất; chuỗi audit, không phải khóa ngoại đến tbl_user.',
  is_deleted: 'Cờ xóa mềm. BaseModel gán false khi lưu mới; DB cho phép NULL và không có DEFAULT.',
};
const escape = value => String(value ?? '—').replaceAll('|', '\\|').replaceAll('\n', '<br>');
const code = value => '`' + escape(value) + '`';
const qualified = t => `${t.schema}.${t.name}`;
const anchor = name => `table-${name.replaceAll('_', '-')}`;
const mapped = t => t.name.startsWith('tbl_');
const group = t => descriptions[t.name]?.group || 'Chưa phân nhóm';
const label = t => descriptions[t.name]?.label || t.name;
const meaning = (t, c) => c.comment || descriptions[t.name]?.columns?.[c.name] || (mapped(t) ? common[c.name] : null);
const fks = t => t.constraints.filter(c => c.type === 'f');
const incoming = name => tables.flatMap(t => fks(t).filter(c => c.referenced_table === name).map(c => ({ table: t, constraint: c })));
const count = predicate => tables.flatMap(t => t.constraints).filter(predicate).length;

for (const t of tables) {
  if (!descriptions[t.name]) throw new Error(`Missing table description: ${t.name}`);
  for (const c of t.columns) if (!meaning(t, c)) throw new Error(`Missing column description: ${t.name}.${c.name}`);
}

function keyNotes(t, col) {
  const result = [];
  for (const c of t.constraints.filter(c => c.columns.includes(col.name))) {
    if (c.type === 'p') result.push(c.columns.length === 1 ? 'PK' : `PK ghép (${c.columns.join(', ')})`);
    if (c.type === 'u') result.push(c.columns.length === 1 ? 'UNIQUE' : `Thuộc UNIQUE (${c.columns.join(', ')})`);
    if (c.type === 'f') result.push(`FK → ${c.referenced_table}.${c.referenced_columns[c.columns.indexOf(col.name)]}`);
  }
  return result.join('; ') || '—';
}

function relation(t, c) {
  const optional = c.columns.some(name => t.columns.find(col => col.name === name).nullable);
  const unique = t.constraints.some(u => ['p', 'u'].includes(u.type) && u.columns.length === c.columns.length && u.columns.every(name => c.columns.includes(name)));
  const primary = t.constraints.find(u => u.type === 'p')?.columns || [];
  const identifying = c.columns.every(name => primary.includes(name));
  return `    ${c.referenced_table} ${optional ? '|o' : '||'}${identifying ? '--' : '..'}${unique ? 'o|' : 'o{'} ${t.name} : "${c.columns.join(', ')}"`;
}

function graph(selected, includeParents = true) {
  const present = new Map(selected.map(t => [t.name, t]));
  const edges = [];
  for (const t of selected) for (const c of fks(t)) {
    if (includeParents) present.set(c.referenced_table, byName.get(c.referenced_table));
    if (present.has(c.referenced_table)) edges.push(relation(t, c));
  }
  const lines = ['erDiagram'];
  for (const t of [...present.values()].sort((a, b) => a.name.localeCompare(b.name))) {
    lines.push(`    ${t.name} {`);
    for (const col of t.columns) {
      const pk = t.constraints.some(c => c.type === 'p' && c.columns.includes(col.name));
      const fk = t.constraints.some(c => c.type === 'f' && c.columns.includes(col.name));
      if (pk || fk) {
        const kind = col.type.replaceAll(' ', '_').replaceAll(',', '_');
        lines.push(`        ${kind} ${col.name} ${[pk && 'PK', fk && 'FK'].filter(Boolean).join(',')}`);
      }
    }
    lines.push('    }');
  }
  lines.push(...edges);
  return lines.join('\n');
}

const current = tables.filter(mapped);
const other = tables.filter(t => !mapped(t));
const currentGroups = [...new Set(current.map(group))];
const otherGroups = [...new Set(other.map(group))];
const timestamp = schema.captured_at;

const doc = [
  '# Đặc tả cơ sở dữ liệu HRM', '',
  `Nguồn: PostgreSQL ${schema.server_version}, database ${code(schema.database)}; thời điểm đọc catalog: ${code(timestamp)}. Chỉ đọc cấu trúc, không xuất bản ghi nhân sự, mật khẩu hay token.`, '',
  `Database có **${tables.length} bảng, ${tables.reduce((n, t) => n + t.columns.length, 0)} cột, ${count(c => c.type === 'f')} khóa ngoại, ${count(c => c.type === 'u')} ràng buộc UNIQUE, ${count(c => c.type === 'c')} CHECK và ${tables.reduce((n, t) => n + t.indexes.length, 0)} chỉ mục**. Các chỉ mục PK/UNIQUE được tính trong tổng chỉ mục.`, '',
  `Có **${current.length} bảng có tiền tố tbl_** được entity của backend hiện tại ánh xạ; **${other.length} bảng còn lại chưa thấy ánh xạ trong mã backend đang đọc**. Cả hai nhóm đều nằm trong schema PostgreSQL ${code('public')}, không phải hai database hoặc hai schema riêng. Không có khóa ngoại nối hai nhóm. Chưa xác minh lịch sử tạo bảng hay ứng dụng khác có sử dụng nhóm thứ hai.`, '',
  '## Cách đọc', '',
  '- **PK**: khóa chính. **FK**: khóa ngoại thực sự khai báo trong PostgreSQL. **UNIQUE**: ràng buộc không trùng.',
  '- **Cho phép NULL** lấy từ DB; quy tắc bắt buộc trên form hoặc trong Java có thể chặt hơn.',
  '- **DEFAULT tại DB** là biểu thức PostgreSQL, không phải giá trị khởi tạo trong Java. Toàn bộ 26 bảng tbl_* hiện không khai báo SQL DEFAULT, kể cả id và cờ xóa mềm.',
  '- Một cột thuộc UNIQUE ghép không có nghĩa cột đó tự nó là duy nhất.',
  '- Ý nghĩa nghiệp vụ bảng tbl_* được đối chiếu entity/service/enums. Ý nghĩa nhóm chưa ánh xạ là diễn giải từ tên cột và cấu trúc, chưa được xác nhận bằng mã nghiệp vụ.',
  '- Ràng buộc dưới đây mô tả DB hiện tại, không suy diễn thêm ràng buộc chỉ tồn tại ở tầng ứng dụng.', '',
  '## Các cột chung của 26 bảng tbl_*', '',
  '| Cột | Ý nghĩa |', '| --- | --- |',
  ...Object.entries(common).map(([name, text]) => `| ${code(name)} | ${escape(text)} |`), '',
  'Mỗi bảng bên dưới vẫn liệt kê đầy đủ các cột chung cùng kiểu/NULL thực tế. BaseModel là lớp kế thừa Java, không phải một bảng riêng.', '',
  '## Danh mục bảng', '',
  '| Bảng | Nhóm | Vai trò | Ánh xạ backend hiện tại |', '| --- | --- | --- | --- |',
  ...tables.map(t => `| [${t.name}](#${anchor(t.name)}) | ${escape(group(t))} | ${escape(label(t))} | ${mapped(t) ? 'Có' : 'Chưa thấy'} |`), '',
  '## Sơ đồ quan hệ', '',
  'Xem [ERD theo phân hệ](ERD.md), [ERD 26 bảng hiện tại](schema-current.mmd) và [ERD 17 bảng chưa ánh xạ](schema-other.mmd). [DDL PostgreSQL](schema.sql) giữ nguyên khai báo bảng, kiểu dữ liệu, ràng buộc và chỉ mục; [snapshot JSON](schema.json) là dữ liệu catalog dùng tạo tài liệu.', '',
  '## Đặc tả từng bảng', '',
];

for (const t of tables) {
  const d = descriptions[t.name];
  doc.push(`<a id="${anchor(t.name)}"></a>`, '', `### ${qualified(t)} — ${label(t)}`, '',
    d.purpose, '', `**Nhóm:** ${group(t)}. **Ánh xạ:** ${mapped(t) ? 'Có trong entity backend hiện tại.' : 'Chưa thấy trong entity/native SQL backend hiện tại; ý nghĩa cột là suy luận từ cấu trúc.'}`, '',
    `**Nguồn mô tả:** ${typeof d.source === 'string' ? d.source : JSON.stringify(d.source)}`, '',
    '| # | Cột | Kiểu PostgreSQL | Cho phép NULL | Khóa | DEFAULT tại DB | Ý nghĩa |',
    '| --- | --- | --- | --- | --- | --- | --- |');
  for (const c of t.columns) doc.push(`| ${c.position} | ${code(c.name)} | ${code(c.type)} | ${c.nullable ? 'Có' : 'Không'} | ${escape(keyNotes(t, c))} | ${c.default == null ? 'Không khai báo' : code(c.default)} | ${escape(meaning(t, c))} |`);
  doc.push('', '**Quan hệ đi ra:**', '');
  if (!fks(t).length) doc.push('Không có khóa ngoại đi ra.');
  for (const c of fks(t)) doc.push(`- ${code(c.columns.join(', '))} → [${c.referenced_schema}.${c.referenced_table}](#${anchor(c.referenced_table)}) (${code(c.referenced_columns.join(', '))}); ON DELETE ${code(c.on_delete)}, ON UPDATE ${code(c.on_update)}.`);
  doc.push('', '**Bảng tham chiếu đến bảng này:**', '');
  const children = incoming(t.name);
  if (!children.length) doc.push('Không có khóa ngoại từ bảng khác tham chiếu vào.');
  for (const { table: child, constraint: c } of children) doc.push(`- [${child.name}](#${anchor(child.name)}): ${code(c.columns.join(', '))} → ${code(c.referenced_columns.join(', '))}.`);
  if (d.enumNotes?.length) doc.push('', '**Quy ước giá trị / trạng thái:**', '', ...d.enumNotes.map(note => `- ${typeof note === 'string' ? note : JSON.stringify(note)}`));
  doc.push('', '**Ràng buộc tại DB:**', '');
  for (const c of t.constraints) doc.push(`- ${code(c.name)}: ${code(c.definition)}${c.validated ? '' : ' (NOT VALID)'}${c.deferrable ? ' (có thể trì hoãn kiểm tra)' : ''}.`);
  doc.push('', '**Chỉ mục:**', '');
  for (const index of t.indexes) doc.push(`- ${code(index.name)}: ${code(index.definition)}${index.valid ? '' : ' (index chưa valid)'}.`);
  doc.push('');
}

doc.push('## Lưu ý khi dùng tài liệu', '',
  '- Khóa UUID của 26 bảng tbl_* và khóa số của phần lớn nhóm còn lại là hai mô hình riêng; cùng tên nghiệp vụ không có nghĩa các bản ghi tự đồng bộ.',
  '- Các thao tác xóa mềm dùng is_deleted; không đồng nhất với ON DELETE của khóa ngoại. ON DELETE chỉ chạy khi có DELETE vật lý.',
  '- Quy tắc một bảng lương đã xác nhận mỗi kỳ đang được service kiểm tra; catalog không có UNIQUE có điều kiện cho quy tắc này.',
  '- Tuyển dụng lưu status theo thứ tự enum (ORDINAL). Đổi thứ tự enum có thể đổi cách diễn giải dữ liệu đã lưu.',
  '- Đây là snapshot cấu trúc. Khi thay đổi entity hoặc chạy migration, cần xuất lại catalog và cập nhật mô tả.', '');

const erd = ['# Sơ đồ quan hệ database hrm', '',
  `Theo khóa ngoại PostgreSQL tại ${code(timestamp)}. Toàn bộ ${tables.length} bảng và ${count(c => c.type === 'f')} quan hệ được mô tả trong hai sơ đồ tổng thể đính kèm.`, '',
  'Ký hiệu: một nét dọc = một; vòng tròn = có thể không có; chân quạ = nhiều. Đầu phía bảng cha cho biết FK có được NULL hay không. Đầu phía bảng con là 0..1 nếu FK có ràng buộc UNIQUE, còn lại 0..n. Đường chấm là quan hệ không tham gia định danh PK. Chỉ các cột PK/FK được đưa lên sơ đồ; [đặc tả](SCHEMA.md) có đầy đủ mọi cột.', '',
  'Các bảng cùng xuất hiện ở nhiều hình là các bảng được tham chiếu giữa phân hệ, không phải bản sao dữ liệu.', '',
  '## 26 bảng được backend hiện tại ánh xạ', '',
];
for (const name of currentGroups) erd.push(`### ${name}`, '', '```mermaid', graph(current.filter(t => group(t) === name)), '```', '');
erd.push('## 17 bảng chưa thấy backend hiện tại ánh xạ', '', 'Cùng nằm trong public, không có FK nối sang nhóm tbl_*. Tên phân hệ và vai trò nhóm này được suy luận từ cấu trúc.', '');
for (const name of otherGroups) erd.push(`### ${name}`, '', '```mermaid', graph(other.filter(t => group(t) === name)), '```', '');
erd.push('## Sơ đồ tổng thể', '', '- [26 bảng tbl_*](schema-current.mmd)', '- [17 bảng còn lại](schema-other.mmd)', '');

fs.writeFileSync(path.join(here, 'SCHEMA.md'), doc.join('\n'));
fs.writeFileSync(path.join(here, 'ERD.md'), erd.join('\n'));
fs.writeFileSync(path.join(here, 'schema-current.mmd'), graph(current, false) + '\n');
fs.writeFileSync(path.join(here, 'schema-other.mmd'), graph(other, false) + '\n');
console.log(`Generated specifications for ${tables.length} tables / ${tables.reduce((n, t) => n + t.columns.length, 0)} columns.`);
