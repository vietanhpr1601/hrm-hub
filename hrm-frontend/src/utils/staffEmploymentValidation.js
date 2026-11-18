import * as Yup from 'yup';

// Older/imported records may not have a known resignation date. Never guess one.
export const resignationDateValidation = () => Yup.date().nullable()
    .typeError('Ngày nghỉ việc không hợp lệ')
    .test('after-start-date', 'Ngày nghỉ việc không được trước ngày vào làm', function (value) {
        const start = this.parent.startDate;
        if (!value || !start) return true;
        const startDay = new Date(start);
        const endDay = new Date(value);
        startDay.setHours(0, 0, 0, 0);
        endDay.setHours(0, 0, 0, 0);
        return endDay >= startDay;
    });
