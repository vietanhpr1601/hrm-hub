import { useMemo, useState } from 'react';
import {
  Accordion, AccordionDetails, AccordionSummary, Box, Checkbox, Chip,
  FormControlLabel, MenuItem, Select, Tooltip, Typography,
} from '@mui/material';
import { ExpandMore, CheckCircleOutline, Block, Tune, PeopleOutline, AccessTime, PaymentsOutlined, EventAvailableOutlined, AccountTreeOutlined, FeedOutlined, NotificationsNone } from '@mui/icons-material';
import { permissionModules } from '../../../utils/permissions.js';

export default function PermissionModules({ catalog, search, selected, onChange, overrides, onOverride, disabled = false }) {
  const [expanded, setExpanded] = useState({});
  const modules = useMemo(() => permissionModules(catalog.filter((item) => !item.adminOnly), search), [catalog, search]);
  const accountMode = !!onOverride;
  if (!Object.keys(modules).length) return <Typography color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>Không tìm thấy quyền phù hợp.</Typography>;
  return <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
    {Object.entries(modules).map(([module, items]) => {
      const count = items.filter((item) => selected.includes(item.code)).length;
      const prefix = items[0].code.split('_')[0];
      const Icon = ({ STAFF: PeopleOutline, ATTENDANCE: AccessTime, PAYROLL: PaymentsOutlined, LEAVE: EventAvailableOutlined, DEPARTMENT: AccountTreeOutlined, BULLETIN: FeedOutlined, NOTIFICATION: NotificationsNone })[prefix] || Tune;
      return <Accordion key={module} expanded={search.trim() ? expanded[module] !== false : !!expanded[module]}
        onChange={(_, value) => setExpanded((current) => ({ ...current, [module]: value }))} disableGutters elevation={0}
        sx={{ border: '1px solid', borderColor: 'divider', borderRadius: '10px !important', '&:before': { display: 'none' } }}>
        <AccordionSummary expandIcon={<ExpandMore />} aria-label={`Quyền ${module}`} sx={{ minHeight: 56 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, width: '100%', pr: 1 }}>
            <Icon fontSize="small" color="action" /><Typography variant="body2" fontWeight={600} sx={{ flex: 1 }}>{module}</Typography>
            <Chip size="small" label={`${count}/${items.length}`} color={count ? 'primary' : 'default'} variant="outlined" />
          </Box>
        </AccordionSummary>
        <AccordionDetails sx={{ pt: 0, pb: 1.5 }}>
          {!accountMode && <FormControlLabel label="Chọn tất cả" sx={{ mb: 0.5 }} control={<Checkbox size="small" disabled={disabled}
            inputProps={{ 'aria-label': `Chọn tất cả quyền ${module}` }} checked={count === items.length} indeterminate={count > 0 && count < items.length}
            onChange={(_, checked) => onChange(checked ? [...new Set([...selected, ...items.map((item) => item.code)])]
              : selected.filter((code) => !items.some((item) => item.code === code)))} />} />}
          <Box sx={{ display: 'grid', gridTemplateColumns: accountMode ? '1fr' : { xs: '1fr', md: '1fr 1fr' }, gap: accountMode ? 0.5 : 0 }}>
            {items.map((item) => accountMode ? <Box key={item.code} sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 0.75, flexWrap: { xs: 'wrap', sm: 'nowrap' } }}>
              <Typography variant="body2" sx={{ flex: 1, minWidth: { xs: 'calc(100% - 32px)', sm: 180 } }}>{item.label}</Typography>
              <Select size="small" value={overrides[item.code] || 'INHERIT'} disabled={disabled}
                inputProps={{ 'aria-label': `Quyền riêng ${item.label}` }} sx={{ minWidth: 150, fontSize: 13 }}
                onChange={(event) => onOverride(item.code, event.target.value)}>
                <MenuItem value="INHERIT">Theo vai trò</MenuItem><MenuItem value="ALLOW">Cho phép</MenuItem><MenuItem value="DENY">Chặn</MenuItem>
              </Select>
              <Tooltip title={selected.includes(item.code) ? 'Được phép' : 'Không có quyền'}>
                <Box component="span" sx={{ display: 'flex' }} aria-label={`${item.label}: ${selected.includes(item.code) ? 'Được phép' : 'Không có quyền'}`}>
                  {selected.includes(item.code) ? <CheckCircleOutline color="success" fontSize="small" /> : <Block color="disabled" fontSize="small" />}
                </Box>
              </Tooltip>
            </Box> : <FormControlLabel key={item.code} label={<Typography variant="body2">{item.label}</Typography>}
              control={<Checkbox size="small" disabled={disabled} checked={selected.includes(item.code)}
                onChange={(_, checked) => onChange(checked ? [...selected, item.code] : selected.filter((code) => code !== item.code))} />} />)}
          </Box>
        </AccordionDetails>
      </Accordion>;
    })}
  </Box>;
}
