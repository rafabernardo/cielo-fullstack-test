const BENEFICIARY_PARAM = 'beneficiary';

// The beneficiary lives in the URL (?beneficiary=) so a reload resumes the same beneficiary.
export const readBeneficiary = () =>
  new URLSearchParams(window.location.search).get(BENEFICIARY_PARAM);

export const writeBeneficiary = (beneficiaryId: string) => {
  const url = new URL(window.location.href);
  url.searchParams.set(BENEFICIARY_PARAM, beneficiaryId);
  window.history.replaceState(null, '', url);
};
